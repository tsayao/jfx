/*
 * Copyright (C) 2013, 2017 Apple Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY APPLE INC. ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL APPLE INC. OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#import <memory>
#import <objc/Protocol.h>
#import <objc/runtime.h>
#import <wtf/HashSet.h>
#import <wtf/SystemFree.h>
#import <wtf/Vector.h>
#import <wtf/text/CString.h>

template<typename T, typename U>
inline std::unique_ptr<T, WTF::SystemFree<T>> adoptSystem(U value)
{
    return std::unique_ptr<T, WTF::SystemFree<T>>(value);
}

inline bool protocolImplementsProtocol(Protocol *candidate, Protocol *target)
{
    unsigned protocolProtocolsCount;
    auto protocolProtocols = adoptSystem<__unsafe_unretained Protocol*[]>(protocol_copyProtocolList(candidate, &protocolProtocolsCount));
    for (unsigned i = 0; i < protocolProtocolsCount; ++i) {
        if (protocol_isEqual(protocolProtocols[i], target))
            return true;
    }
    return false;
}

inline void forEachProtocolImplementingProtocol(Class cls, Protocol *target, void (^callback)(Protocol *, bool& stop))
{
    ASSERT(cls);
    ASSERT(target);

    Vector<Protocol*> worklist;
    HashSet<void*> visited;

    // Initially fill the worklist with the Class's protocols.
    {
        unsigned protocolsCount;
        auto protocols = adoptSystem<__unsafe_unretained Protocol*[]>(class_copyProtocolList(cls, &protocolsCount));
        worklist.append(std::span { protocols.get(), protocolsCount });
    }

    bool stop = false;
    while (!worklist.isEmpty()) {
        Protocol *protocol = worklist.last();
        worklist.removeLast();

        // Are we encountering this Protocol for the first time?
        if (!visited.add((__bridge void*)protocol).isNewEntry)
            continue;

        // If it implements the protocol, make the callback.
        if (protocolImplementsProtocol(protocol, target)) {
            callback(protocol, stop);
            if (stop)
                break;
        }

        // Add incorporated protocols to the worklist.
        {
            unsigned protocolsCount;
            auto protocols = adoptSystem<__unsafe_unretained Protocol*[]>(protocol_copyProtocolList(protocol, &protocolsCount));
            worklist.append(std::span { protocols.get(), protocolsCount });
        }
    }
}

inline void forEachMethodInClass(Class cls, void (^callback)(Method))
{
    unsigned count;
    auto methods = adoptSystem<Method[]>(class_copyMethodList(cls, &count));
    for (unsigned i = 0; i < count; ++i)
        callback(methods[i]);
}

inline void forEachMethodInProtocol(Protocol *protocol, BOOL isRequiredMethod, BOOL isInstanceMethod, void (^callback)(SEL, const char*))
{
    unsigned count;
    auto methods = adoptSystem<objc_method_description[]>(protocol_copyMethodDescriptionList(protocol, isRequiredMethod, isInstanceMethod, &count));
    for (unsigned i = 0; i < count; ++i)
        callback(methods[i].name, methods[i].types);
}

inline void forEachPropertyInProtocol(Protocol *protocol, void (^callback)(objc_property_t))
{
    unsigned count;
    auto properties = adoptSystem<objc_property_t[]>(protocol_copyPropertyList(protocol, &count));
    for (unsigned i = 0; i < count; ++i)
        callback(properties[i]);
}

template<char open, char close>
void skipPair(const char*& position)
{
    size_t count = 1;
    do {
        char c = *position++;
        if (!c)
            @throw [NSException exceptionWithName:NSInternalInconsistencyException reason:@"Malformed type encoding" userInfo:nil];
        if (c == open)
            ++count;
        else if (c == close)
            --count;
    } while (count);
}

class StringRange {
    WTF_MAKE_NONCOPYABLE(StringRange);
public:
    StringRange(const char* begin, const char* end)
        : m_string({ begin, end })
    { }
    operator const char*() const { return m_string.data(); }
    const char* get() const { return m_string.data(); }

private:
    CString m_string;
};

class StructBuffer {
    WTF_MAKE_NONCOPYABLE(StructBuffer);
public:
    StructBuffer(const char* encodedType)
    {
        NSUInteger size, alignment;
        NSGetSizeAndAlignment(encodedType, &size, &alignment);
        m_buffer = fastAlignedMalloc(alignment, size);
    }

    ~StructBuffer() { fastAlignedFree(m_buffer); }
    operator void*() const { return m_buffer; }

private:
    void* m_buffer;
};

template<typename DelegateType>
typename DelegateType::ResultType parseObjCType(const char*& position)
{
    ASSERT(*position);

    switch (*position++) {
    case 'c':
        return DelegateType::template typeInteger<char>();
    case 'i':
        return DelegateType::template typeInteger<int>();
    case 's':
        return DelegateType::template typeInteger<short>();
    case 'l':
        return DelegateType::template typeInteger<long>();
    case 'q':
        return DelegateType::template typeDouble<long long>();
    case 'C':
        return DelegateType::template typeInteger<unsigned char>();
    case 'I':
        return DelegateType::template typeInteger<unsigned>();
    case 'S':
        return DelegateType::template typeInteger<unsigned short>();
    case 'L':
        return DelegateType::template typeInteger<unsigned long>();
    case 'Q':
        return DelegateType::template typeDouble<unsigned long long>();
    case 'f':
        return DelegateType::template typeDouble<float>();
    case 'd':
        return DelegateType::template typeDouble<double>();
    case 'B':
        return DelegateType::typeBool();
    case 'v':
        return DelegateType::typeVoid();

    case '@': { // An object (whether statically typed or typed id)
        if (position[0] == '?' && position[1] == '<') {
            position += 2;
            const char* begin = position;
            skipPair<'<','>'>(position);
            return DelegateType::typeBlock(begin, position - 1);
        }

        if (*position == '"') {
            const char* begin = position + 1;
            const char* protocolPosition = strchr(begin, '<');
            const char* endOfType = strchr(begin, '"');
            position = endOfType + 1;

            // There's no protocol involved in this type, so just handle the class name.
            if (!protocolPosition || protocolPosition > endOfType)
                return DelegateType::typeOfClass(begin, endOfType);
            // We skipped the class name and went straight to the protocol, so this is an id type.
            if (begin == protocolPosition)
                return DelegateType::typeId();
            // We have a class name with a protocol. For now, ignore the protocol.
            return DelegateType::typeOfClass(begin, protocolPosition);
        }

        return DelegateType::typeId();
    }

    case '{': { // {name=type...} A structure
        const char* begin = position - 1;
        skipPair<'{','}'>(position);
        return DelegateType::typeStruct(begin, position);
    }

    // NOT supporting C strings, arrays, pointers, unions, bitfields, function pointers.
    case '*': // A character string (char *)
    case '[': // [array type] An array
    case '(': // (name=type...) A union
    case 'b': // bnum A bit field of num bits
    case '^': // ^type A pointer to type
    case '?': // An unknown type (among other things, this code is used for function pointers)
    // NOT supporting Objective-C Class, SEL
    case '#': // A class object (Class)
    case ':': // A method selector (SEL)
    default:
        return nil;
    }
}

extern "C" {
    // Forward declare some Objective-C runtime internal methods that are not API.
    const char *_protocol_getMethodTypeEncoding(Protocol *, SEL, BOOL isRequiredMethod, BOOL isInstanceMethod);
    bool _Block_has_signature(void *);
    const char * _Block_signature(void *);
}
