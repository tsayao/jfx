/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package test.javafx.scene.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static test.com.sun.javafx.scene.control.infrastructure.ControlSkinFactory.attemptGC;
import static test.com.sun.javafx.scene.control.infrastructure.ControlTestUtils.assertStyleClassContains;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.CellShim;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableCellShim;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableColumn.CellEditEvent;
import javafx.scene.control.TreeTablePosition;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.skin.TreeTableCellSkin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import com.sun.javafx.tk.Toolkit;
import test.com.sun.javafx.scene.control.infrastructure.StageLoader;
import test.com.sun.javafx.scene.control.infrastructure.VirtualFlowTestUtils;

public class TreeTableCellTest {
    private TreeTableCellShim<String, String> cell;
    private TreeTableView<String> tree;
    private TreeTableRow<String> row;

    private static final String ROOT = "Root";
    private static final String APPLES = "Apples";
    private static final String ORANGES = "Oranges";
    private static final String PEARS = "Pears";

    private TreeItem<String> root;
    private TreeItem<String> apples;
    private TreeItem<String> oranges;
    private TreeItem<String> pears;
    private StageLoader stageLoader;

    private TreeTableColumn<String, String> editingColumn;

    @BeforeEach
    public void setup() {
        Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> {
            if (throwable instanceof RuntimeException) {
                throw (RuntimeException)throwable;
            } else {
                Thread.currentThread().getThreadGroup().uncaughtException(thread, throwable);
            }
        });

        cell = new TreeTableCellShim<>();

        root = new TreeItem<>(ROOT);
        apples = new TreeItem<>(APPLES);
        oranges = new TreeItem<>(ORANGES);
        pears = new TreeItem<>(PEARS);
        root.getChildren().addAll(apples, oranges, pears);

        tree = new TreeTableView<>(root);
        root.setExpanded(true);
        editingColumn = new TreeTableColumn<>("TEST");

        row = new TreeTableRow<>();
    }

    @AfterEach
    public void cleanup() {
        if (stageLoader != null) stageLoader.dispose();
        Thread.currentThread().setUncaughtExceptionHandler(null);
    }


    /*********************************************************************
     * Tests for the constructors                                        *
     ********************************************************************/

    @Test public void styleClassIs_tree_cell_byDefault() {
        assertStyleClassContains(cell, "tree-table-cell");
    }

    // The item should be null by default because the index is -1 by default
    @Test public void itemIsNullByDefault() {
        assertNull(cell.getItem());
    }

    /*********************************************************************
     * Tests for the item property. It should be updated whenever the    *
     * index, or treeView changes, including the treeView's items.       *
     ********************************************************************/

    @Disabled // TODO file bug!
    @Test public void itemMatchesIndexWithinTreeItems() {
        cell.updateIndex(0);
        cell.updateTreeTableView(tree);
        assertSame(ROOT, cell.getItem());
        assertSame(root, cell.getTableRow().getTreeItem());
        cell.updateIndex(1);
        assertSame(APPLES, cell.getItem());
        assertSame(apples, cell.getTableRow().getTreeItem());
    }

    @Disabled // TODO file bug!
    @Test public void itemMatchesIndexWithinTreeItems2() {
        cell.updateTreeTableView(tree);
        cell.updateIndex(0);
        assertSame(ROOT, cell.getItem());
        assertSame(root, cell.getTableRow().getTreeItem());
        cell.updateIndex(1);
        assertSame(APPLES, cell.getItem());
        assertSame(apples, cell.getTableRow().getTreeItem());
    }

    @Test public void itemIsNullWhenIndexIsOutOfRange() {
        cell.updateIndex(50);
        cell.updateTreeTableView(tree);
        assertNull(cell.getItem());
    }

    @Test public void treeItemIsNullWhenIndexIsOutOfRange() {
        cell.updateIndex(50);
        cell.updateTableRow(row);
        cell.updateTreeTableView(tree);
        assertNull(cell.getTableRow().getTreeItem());
    }

    @Test public void itemIsNullWhenIndexIsOutOfRange2() {
        cell.updateTreeTableView(tree);
        cell.updateIndex(50);
        assertNull(cell.getItem());
    }

    // Above were the simple tests. Now we check various circumstances
    // to make sure the item is updated correctly.

    @Disabled // TODO file bug!
    @Test public void itemIsUpdatedWhenItWasOutOfRangeButUpdatesToTreeTableViewItemsMakesItInRange() {
        cell.updateIndex(4);
        cell.updateTreeTableView(tree);
        root.getChildren().addAll(new TreeItem<>("Pumpkin"), new TreeItem<>("Lemon"));
        assertSame("Pumpkin", cell.getItem());
    }

    @Disabled // TODO file bug!
    @Test public void itemIsUpdatedWhenItWasInRangeButUpdatesToTreeTableViewItemsMakesItOutOfRange() {
        cell.updateIndex(2);
        cell.updateTreeTableView(tree);
        assertSame(ORANGES, cell.getItem());
        root.getChildren().remove(oranges);
        assertNull(cell.getTableRow().getTreeItem());
        assertNull(cell.getItem());
    }

    @Disabled // TODO file bug!
    @Test public void itemIsUpdatedWhenTreeTableViewItemsIsUpdated() {
        // set cell index to point to 'Apples'
        cell.updateIndex(1);
        cell.updateTreeTableView(tree);
        assertSame(APPLES, cell.getItem());
        assertSame(apples, cell.getTableRow().getTreeItem());

        // then update the root children list so that the 1st item (including root),
        // is no longer 'Apples', but 'Lime'
        root.getChildren().set(0, new TreeItem<>("Lime"));
        assertEquals("Lime", cell.getItem());
    }

    @Disabled // TODO file bug!
    @Test public void itemIsUpdatedWhenTreeTableViewItemsHasNewItemInsertedBeforeIndex() {
        cell.updateIndex(2);
        cell.updateTreeTableView(tree);
        assertSame(ORANGES, cell.getItem());
        assertSame(oranges, cell.getTableRow().getTreeItem());
        String previous = APPLES;
        root.getChildren().add(0, new TreeItem<>("Lime"));
        assertEquals(previous, cell.getItem());
    }

//    @Test public void itemIsUpdatedWhenTreeTableViewItemsHasItemRemovedBeforeIndex() {
//        cell.updateIndex(1);
//        cell.updateTreeTableView(tree);
//        assertSame(model.get(1), cell.getItem());
//        String other = model.get(2);
//        model.remove(0);
//        assertEquals(other, cell.getItem());
//    }

    @Disabled // TODO file bug!
    @Test public void itemIsUpdatedWhenTreeTableViewItemsIsReplaced() {
        cell.updateIndex(1);
        cell.updateTreeTableView(tree);
        root.getChildren().setAll(new TreeItem<>("Water"), new TreeItem<>("Juice"), new TreeItem<>("Soda"));
        assertEquals("Water", cell.getItem());
    }

    @Disabled // TODO file bug!
    @Test public void itemIsUpdatedWhenTreeTableViewIsReplaced() {
        cell.updateIndex(2);
        cell.updateTreeTableView(tree);
        TreeItem<String> newRoot = new TreeItem<>();
        newRoot.setExpanded(true);
        newRoot.getChildren().setAll(new TreeItem<>("Water"), new TreeItem<>("Juice"), new TreeItem<>("Soda"));
        TreeTableView<String> treeView2 = new TreeTableView<>(newRoot);
        cell.updateTreeTableView(treeView2);
        assertEquals("Juice", cell.getItem());
    }

    @Test public void replaceItemsWithANull() {
        cell.updateIndex(0);
        cell.updateTreeTableView(tree);
        tree.setRoot(null);
        assertNull(cell.getItem());
    }

//    @Test public void replaceItemsWithANull_ListenersRemovedFromFormerList() {
//        cell.updateIndex(0);
//        cell.updateTreeTableView(tree);
//        ListChangeListener listener = getListChangeListener(cell, "weakItemsListener");
//        assertListenerListContains(model, listener);
//        tree.setRoot(null);
//        assertListenerListDoesNotContain(model, treeener);
//    }
//
    @Disabled // TODO file bug!
    @Test public void replaceANullItemsWithNotNull() {
        cell.updateIndex(1);
        cell.updateTreeTableView(tree);
        tree.setRoot(null);

        TreeItem<String> newRoot = new TreeItem<>();
        newRoot.setExpanded(true);
        newRoot.getChildren().setAll(new TreeItem<>("Water"), new TreeItem<>("Juice"), new TreeItem<>("Soda"));
        tree.setRoot(newRoot);
        assertEquals("Water", cell.getItem());
    }


    /*********************************************************************
     * Tests for all things related to editing one of these guys         *
     ********************************************************************/

    // startEdit()
    @Disabled // TODO file bug!
    @Test public void editOnTreeTableViewResultsInEditingInCell() {
        tree.setEditable(true);
        cell.updateTreeTableView(tree);
        cell.updateIndex(1);
        tree.edit(1, null);
        assertTrue(cell.isEditing());
    }

    @Test public void editOnTreeTableViewResultsInNotEditingInCellWhenDifferentIndex() {
        tree.setEditable(true);
        cell.updateTreeTableView(tree);
        cell.updateIndex(1);
        tree.edit(0, null);
        assertFalse(cell.isEditing());
    }

    @Test public void editCellWithNullTreeTableViewResultsInNoExceptions() {
        cell.updateTreeTableView(tree);
        cell.updateIndex(1);
        cell.startEdit();
    }

    @Test public void editCellOnNonEditableTreeDoesNothing() {
        cell.updateIndex(1);
        cell.updateTreeTableView(tree);
        cell.startEdit();
        assertFalse(cell.isEditing());
        assertNull(tree.getEditingCell());
    }

    @Test public void editCellWithTreeResultsInUpdatedEditingIndexProperty() {
        setupForEditing();
        cell.updateIndex(1);
        cell.startEdit();
        assertEquals(apples, tree.getEditingCell().getTreeItem());
    }

    @Test public void editCellWithTreeNoColumnResultsInUpdatedEditingIndexProperty() {
        // note: cell index must be != -1 because table.edit(-1, null) sets editingCell to null
        cell.updateIndex(1);
        setupForcedEditing(tree, null);
        cell.startEdit();
        assertTrue(cell.isEditing());
        assertNotNull(tree.getEditingCell());
        assertEquals(apples, tree.getEditingCell().getTreeItem());
    }

//    @Ignore // TODO file bug!
//    @Test public void editCellFiresEventOnTree() {
//        tree.setEditable(true);
//        cell.updateTreeTableView(tree);
//        cell.updateIndex(2);
//        final boolean[] called = new boolean[] { false };
//        tree.setOnEditStart(new EventHandler<TreeTableView.EditEvent<String>>() {
//            @Override public void handle(TreeTableView.EditEvent<String> event) {
//                called[0] = true;
//            }
//        });
//        cell.startEdit();
//        assertTrue(called[0]);
//    }

    // commitEdit()
    @Test public void commitWhenTreeIsNullIsOK() {
        cell.updateTreeTableView(tree);
        cell.updateIndex(1);
        cell.startEdit();
        cell.commitEdit("Watermelon");
    }

    @Disabled // TODO file bug!
    @Test public void commitWhenTreeIsNotNullWillUpdateTheItemsTree() {
        tree.setEditable(true);
        cell.updateTreeTableView(tree);
        cell.updateIndex(1);
        cell.startEdit();
        cell.commitEdit("Watermelon");
        assertEquals("Watermelon", tree.getRoot().getChildren().get(0).getValue());
    }

//    @Ignore // TODO file bug!
//    @Test public void commitSendsEventToTree() {
//        tree.setEditable(true);
//        cell.updateTreeTableView(tree);
//        cell.updateIndex(1);
//        cell.startEdit();
//        final boolean[] called = new boolean[] { false };
//        tree.setOnEditCommit(new EventHandler<TreeTableView.EditEvent<String>>() {
//            @Override public void handle(TreeTableView.EditEvent<String> event) {
//                called[0] = true;
//            }
//        });
//        cell.commitEdit("Watermelon");
//        assertTrue(called[0]);
//    }

    @Test public void afterCommitTreeTableViewEditingCellIsNull() {
        tree.setEditable(true);
        cell.updateTreeTableView(tree);
        cell.updateIndex(1);
        cell.startEdit();
        cell.commitEdit("Watermelon");
        assertNull(tree.getEditingCell());
        assertFalse(cell.isEditing());
    }

    // cancelEdit()
    @Test public void cancelEditCanBeCalledWhileTreeTableViewIsNull() {
        cell.updateTreeTableView(tree);
        cell.updateIndex(1);
        cell.startEdit();
        cell.cancelEdit();
    }

//    @Ignore // TODO file bug!
//    @Test public void cancelEditFiresChangeEvent() {
//        tree.setEditable(true);
//        cell.updateTreeTableView(tree);
//        cell.updateIndex(1);
//        cell.startEdit();
//        final boolean[] called = new boolean[] { false };
//        tree.setOnEditCancel(new EventHandler<TreeTableView.EditEvent<String>>() {
//            @Override public void handle(TreeTableView.EditEvent<String> event) {
//                called[0] = true;
//            }
//        });
//        cell.cancelEdit();
//        assertTrue(called[0]);
//    }

    @Test public void cancelSetsTreeTableViewEditingIndexToNegativeOne() {
        tree.setEditable(true);
        cell.updateTreeTableView(tree);
        cell.updateIndex(1);
        cell.startEdit();
        cell.cancelEdit();
        assertNull(tree.getEditingCell());
        assertFalse(cell.isEditing());
    }

    // When the tree view item's change and affects a cell that is editing, then what?
    // When the tree cell's index is changed while it is editing, then what?


    /*********************************************************************
     * Tests for the treeTableView property                              *
     ********************************************************************/

    @Test public void updateTreeTableViewUpdatesTreeTableView() {
        cell.updateTreeTableView(tree);
        assertSame(tree, cell.getTreeTableView());
        assertSame(tree, cell.treeTableViewProperty().get());
    }

    @Test public void canSetTreeTableViewBackToNull() {
        cell.updateTreeTableView(tree);
        cell.updateTreeTableView(null);
        assertNull(cell.getTreeTableView());
        assertNull(cell.treeTableViewProperty().get());
    }

    @Test public void treeTableViewPropertyReturnsCorrectBean() {
        assertSame(cell, cell.treeTableViewProperty().getBean());
    }

    @Test public void updateTreeTableViewWithNullFocusModelResultsInNoException() {
        cell.updateTreeTableView(tree);
        tree.setFocusModel(null);
        cell.updateTreeTableView(new TreeTableView());
    }

    @Test public void updateTreeTableViewWithNullFocusModelResultsInNoException2() {
        tree.setFocusModel(null);
        cell.updateTreeTableView(tree);
        cell.updateTreeTableView(new TreeTableView());
    }

    @Test public void updateTreeTableViewWithNullFocusModelResultsInNoException3() {
        cell.updateTreeTableView(tree);
        TreeTableView tree2 = new TreeTableView();
        tree2.setFocusModel(null);
        cell.updateTreeTableView(tree2);
    }

    @Test public void updateTreeTableViewWithNullSelectionModelResultsInNoException() {
        cell.updateTreeTableView(tree);
        tree.setSelectionModel(null);
        cell.updateTreeTableView(new TreeTableView());
    }

    @Test public void updateTreeTableViewWithNullSelectionModelResultsInNoException2() {
        tree.setSelectionModel(null);
        cell.updateTreeTableView(tree);
        cell.updateTreeTableView(new TreeTableView());
    }

    @Test public void updateTreeTableViewWithNullSelectionModelResultsInNoException3() {
        cell.updateTreeTableView(tree);
        TreeTableView tree2 = new TreeTableView();
        tree2.setSelectionModel(null);
        cell.updateTreeTableView(tree2);
    }

    @Test public void updateTreeTableViewWithNullItemsResultsInNoException() {
        cell.updateTreeTableView(tree);
        tree.setRoot(null);
        cell.updateTreeTableView(new TreeTableView());
    }

    @Test public void updateTreeTableViewWithNullItemsResultsInNoException2() {
        tree.setRoot(null);
        cell.updateTreeTableView(tree);
        cell.updateTreeTableView(new TreeTableView());
    }

    @Test public void updateTreeTableViewWithNullItemsResultsInNoException3() {
        cell.updateTreeTableView(tree);
        TreeTableView tree2 = new TreeTableView();
        tree2.setRoot(null);
        cell.updateTreeTableView(tree2);
    }

    @Test public void treeTableViewIsNullByDefault() {
        assertNull(cell.getTreeTableView());
        assertNull(cell.treeTableViewProperty().get());
    }

    @Test public void treeTableViewPropertyNameIs_treeTableView() {
        assertEquals("treeTableView", cell.treeTableViewProperty().getName());
    }

    @Test public void checkTableRowPropertyName() {
        assertEquals("tableRow", cell.tableRowProperty().getName());
    }

    @Test public void checkTableColumnPropertyName() {
        assertEquals("tableColumn", cell.tableColumnProperty().getName());
    }

    @Test public void checkTableRowProperty() {
        cell.updateTreeTableView(tree);
        cell.updateTableRow(row);
        assertSame(row, cell.getTableRow());
        assertSame(row, cell.tableRowProperty().get());
        assertFalse(cell.tableRowProperty() instanceof ObjectProperty);
    }

    @Test public void checkTableColumnProperty() {
        TreeTableColumn<String, String> column = new TreeTableColumn<>();
        cell.updateTreeTableView(tree);
        cell.updateTableColumn(column);
        assertSame(column, cell.getTableColumn());
        assertSame(column, cell.tableColumnProperty().get());
        assertFalse(cell.tableColumnProperty() instanceof ObjectProperty);
    }

    private int rt_29923_count = 0;
    @Test public void test_rt_29923() {
        // setup test
        cell = new TreeTableCellShim<>() {
            @Override public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                rt_29923_count++;
            }
        };
        TreeTableColumn col = new TreeTableColumn("TEST");
        col.setCellValueFactory(param -> null);
        tree.getColumns().add(col);
        cell.updateTableColumn(col);
        cell.updateTreeTableView(tree);

        // set index to 0, which results in the cell value factory returning
        // null, but because the number of items is 3, this is a valid value
        cell.updateIndex(0);
        assertNull(cell.getItem());
        assertFalse(cell.isEmpty());
        assertEquals(1, rt_29923_count);

        cell.updateIndex(1);
        assertNull(cell.getItem());
        assertFalse(cell.isEmpty());

        // This test used to be as shown below....but due to JDK-8122970, it changed
        // to the enabled code beneath. Refer to the first comment in JDK-8122970
        // for more detail, but in short we can't optimise and not call updateItem
        // when the new and old items are the same - doing so means we can end
        // up with bad bindings, etc in the individual cells (in other words,
        // even if their item has not changed, the rest of their state may have)
//        assertEquals(1, rt_29923_count);    // even though the index has changed,
//                                            // the item is the same, so we don't
//                                            // update the cell item.
        assertEquals(2, rt_29923_count);
    }

    @Test public void test_rt_33106() {
        cell.updateTreeTableView(tree);
        tree.setRoot(null);
        cell.updateIndex(1);
    }

    @Test public void test_rt36715_idIsNullAtStartup() {
        assertNull(cell.getId());
    }

    @Test public void test_rt36715_idIsSettable() {
        cell.setId("test-id");
        assertEquals("test-id", cell.getId());
    }

    @Test public void test_rt36715_columnHeaderIdMirrorsTableColumnId_setIdBeforeHeaderInstantiation() {
        test_rt36715_cellPropertiesMirrorTableColumnProperties(true, true, false, false, false);
    }

    @Test public void test_rt36715_columnHeaderIdMirrorsTableColumnId_setIdAfterHeaderInstantiation() {
        test_rt36715_cellPropertiesMirrorTableColumnProperties(true, false, false, false, false);
    }

    @Test public void test_rt36715_columnHeaderIdMirrorsTableColumnId_setIdBeforeHeaderInstantiation_setValueOnCell() {
        test_rt36715_cellPropertiesMirrorTableColumnProperties(true, true, false, false, true);
    }

    @Test public void test_rt36715_columnHeaderIdMirrorsTableColumnId_setIdAfterHeaderInstantiation_setValueOnCell() {
        test_rt36715_cellPropertiesMirrorTableColumnProperties(true, false, false, false, true);
    }

    @Test public void test_rt36715_styleIsEmptyStringAtStartup() {
        assertEquals("", cell.getStyle());
    }

    @Test public void test_rt36715_styleIsSettable() {
        cell.setStyle("-fx-border-color: red");
        assertEquals("-fx-border-color: red", cell.getStyle());
    }

    @Test public void test_rt36715_columnHeaderStyleMirrorsTableColumnStyle_setStyleBeforeHeaderInstantiation() {
        test_rt36715_cellPropertiesMirrorTableColumnProperties(false, false, true, true, false);
    }

    @Test public void test_rt36715_columnHeaderStyleMirrorsTableColumnStyle_setStyleAfterHeaderInstantiation() {
        test_rt36715_cellPropertiesMirrorTableColumnProperties(false, false, true, false, false);
    }

    @Test public void test_rt36715_columnHeaderStyleMirrorsTableColumnStyle_setStyleBeforeHeaderInstantiation_setValueOnCell() {
        test_rt36715_cellPropertiesMirrorTableColumnProperties(false, false, true, true, true);
    }

    @Test public void test_rt36715_columnHeaderStyleMirrorsTableColumnStyle_setStyleAfterHeaderInstantiation_setValueOnCell() {
        test_rt36715_cellPropertiesMirrorTableColumnProperties(false, false, true, false, true);
    }

    private void test_rt36715_cellPropertiesMirrorTableColumnProperties(
            boolean setId, boolean setIdBeforeHeaderInstantiation,
            boolean setStyle, boolean setStyleBeforeHeaderInstantiation,
            boolean setValueOnCell) {

        TreeTableColumn column = new TreeTableColumn("Column");
        tree.getColumns().add(column);

        if (setId && setIdBeforeHeaderInstantiation) {
            column.setId("test-id");
        }
        if (setStyle && setStyleBeforeHeaderInstantiation) {
            column.setStyle("-fx-border-color: red");
        }

        StageLoader sl = new StageLoader(tree);
        TreeTableCell cell = (TreeTableCell) VirtualFlowTestUtils.getCell(tree, 0, 0);

        // the default value takes precedence over the value set in the TableColumn
        if (setValueOnCell) {
            if (setId) {
                cell.setId("cell-id");
            }
            if (setStyle) {
                cell.setStyle("-fx-border-color: green");
            }
        }

        if (setId && ! setIdBeforeHeaderInstantiation) {
            column.setId("test-id");
        }
        if (setStyle && ! setStyleBeforeHeaderInstantiation) {
            column.setStyle("-fx-border-color: red");
        }

        if (setId) {
            if (setValueOnCell) {
                assertEquals("cell-id", cell.getId());
            } else {
                assertEquals("test-id", cell.getId());
            }
        }
        if (setStyle) {
            if (setValueOnCell) {
                assertEquals("-fx-border-color: green", cell.getStyle());
            } else {
                assertEquals("-fx-border-color: red", cell.getStyle());
            }
        }

        sl.dispose();
    }

    @Test public void test_jdk_8151524() {
        TreeTableCell cell = new TreeTableCell();
        cell.setSkin(new TreeTableCellSkin(cell));
    }

    /**
     * The {@link TreeTableRow} should never be null inside the {@link TreeTableCell} during auto sizing.
     * Note: The auto sizing is triggered as soon as the table has a scene - so when the {@link StageLoader} is created.
     * See also: JDK-8251481
     */
    @Test
    public void testRowIsNotNullWhenAutoSizing() {
        TreeTableColumn<String, String> treeTableColumn = new TreeTableColumn<>();
        treeTableColumn.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                assertNotNull(getTableRow());
            }
        });
        tree.getColumns().add(treeTableColumn);

        stageLoader = new StageLoader(tree);
    }

    /**
     * The item of the {@link TreeTableRow} should not be null, when the {@link TreeTableCell} is not empty.
     * See also: JDK-8251483
     */
    @Test
    public void testRowItemIsNotNullForNonEmptyCell() {
        TreeTableColumn<String, String> treeTableColumn = new TreeTableColumn<>();
        treeTableColumn.setCellValueFactory(cc -> new SimpleStringProperty(cc.getValue().getValue()));
        treeTableColumn.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (!empty) {
                    assertNotNull(getTableRow().getItem());
                }
            }
        });
        tree.getColumns().add(treeTableColumn);

        stageLoader = new StageLoader(tree);

        // Will create a new row and cell.
        tree.getRoot().getChildren().add(new TreeItem<>("newItem"));
        Toolkit.getToolkit().firePulse();
    }

    /**
     * Table: Editable<br>
     * Row: Not editable<br>
     * Column: Editable<br>
     * Expected: Cell can not be edited because the row is not editable.
     */
    @Test
    public void testCellInUneditableRowIsNotEditable() {
        tree.setEditable(true);
        row.setEditable(false);

        TreeTableColumn<String, String> treeTableColumn = new TreeTableColumn<>();
        treeTableColumn.setEditable(true);
        tree.getColumns().add(treeTableColumn);

        cell.updateTableColumn(treeTableColumn);
        cell.updateTableRow(row);
        cell.updateTreeTableView(tree);

        cell.updateIndex(0);
        cell.startEdit();

        assertFalse(cell.isEditing());
    }

    /**
     * Table: Not editable<br>
     * Row: Editable<br>
     * Column: Editable<br>
     * Expected: Cell can not be edited because the table is not editable.
     */
    @Test
    public void testCellInUneditableTableIsNotEditable() {
        tree.setEditable(false);
        row.setEditable(true);

        TreeTableColumn<String, String> treeTableColumn = new TreeTableColumn<>();
        treeTableColumn.setEditable(true);
        tree.getColumns().add(treeTableColumn);

        cell.updateTableColumn(treeTableColumn);
        cell.updateTableRow(row);
        cell.updateTreeTableView(tree);

        cell.updateIndex(0);
        cell.startEdit();

        assertFalse(cell.isEditing());
    }

    /**
     * Table: Editable<br>
     * Row: Editable<br>
     * Column: Not editable<br>
     * Expected: Cell can not be edited because the column is not editable.
     */
    @Test
    public void testCellInUneditableColumnIsNotEditable() {
        tree.setEditable(true);
        row.setEditable(true);

        TreeTableColumn<String, String> treeTableColumn = new TreeTableColumn<>();
        treeTableColumn.setEditable(false);
        tree.getColumns().add(treeTableColumn);

        cell.updateTableColumn(treeTableColumn);
        cell.updateTableRow(row);
        cell.updateTreeTableView(tree);

        cell.updateIndex(0);
        cell.startEdit();

        assertFalse(cell.isEditing());
    }

    /**
     * Basic config of treeTable-/cell to allow testing of editEvents:
     * table is editable, has editingColumn and cell is configured with table and column.
     */
    private void setupForEditing() {
        tree.setEditable(true);
        tree.getColumns().add(editingColumn);
        // FIXME: default cell (of tableColumn) needs not-null value for firing cancel
        editingColumn.setCellValueFactory(cc -> new SimpleObjectProperty<>(""));

        cell.updateTreeTableView(tree);
        cell.updateTableColumn(editingColumn);
    }

    @Test
    public void testEditCancelEventAfterCancelOnCell() {
        setupForEditing();
        int editingIndex = 1;
        cell.updateIndex(editingIndex);
        tree.edit(editingIndex, editingColumn);
        TreeTablePosition<?,?> editingPosition = tree.getEditingCell();
        List<CellEditEvent<?, ?>> events = new ArrayList<>();
        editingColumn.setOnEditCancel(events::add);
        cell.cancelEdit();
        assertEquals(1, events.size(), "column must have received editCancel");
        assertEquals(editingPosition, events.get(0).getTreeTablePosition(), "editing location of cancel event");
    }

    @Test
    public void testEditCancelEventAfterCancelOnTreeTable() {
        setupForEditing();
        int editingIndex = 1;
        cell.updateIndex(editingIndex);
        tree.edit(editingIndex, editingColumn);
        TreeTablePosition<?, ?> editingPosition = tree.getEditingCell();
        List<CellEditEvent<?, ?>> events = new ArrayList<>();
        editingColumn.setOnEditCancel(events::add);
        tree.edit(-1, null);
        assertEquals(1, events.size(), "column must have received editCancel");
        assertEquals(editingPosition, events.get(0).getTreeTablePosition(), "editing location of cancel event");
    }

    @Test
    public void testEditCancelEventAfterCellReuse() {
        setupForEditing();
        int editingIndex = 1;
        cell.updateIndex(editingIndex);
        tree.edit(editingIndex, editingColumn);
        TreeTablePosition<?, ?> editingPosition = tree.getEditingCell();
        List<CellEditEvent<?, ?>> events = new ArrayList<>();
        editingColumn.setOnEditCancel(events::add);
        cell.updateIndex(0);
        assertEquals(1, events.size(), "column must have received editCancel");
        assertEquals(editingPosition, events.get(0).getTreeTablePosition(), "editing location of cancel event");
    }

    @Test
    public void testEditCancelEventAfterCollapse() {
        setupForEditing();
        stageLoader = new StageLoader(tree);
        int editingIndex = 1;
        tree.edit(editingIndex, editingColumn);
        TreeTablePosition<?, ?> editingPosition = tree.getEditingCell();
        List<CellEditEvent<?, ?>> events = new ArrayList<>();
        editingColumn.setOnEditCancel(events::add);
        root.setExpanded(false);
        Toolkit.getToolkit().firePulse();
        assertEquals(1, events.size(), "column must have received editCancel");
        assertEquals(editingPosition, events.get(0).getTreeTablePosition(), "editing location of cancel event");
    }

    @Test
    public void testEditCancelEventAfterModifyItems() {
        setupForEditing();
        stageLoader = new StageLoader(tree);
        int editingIndex = 2;
        tree.edit(editingIndex, editingColumn);
        TreeTablePosition<?, ?> editingPosition = tree.getEditingCell();
        List<CellEditEvent<?, ?>> events = new ArrayList<>();
        editingColumn.setOnEditCancel(events::add);
        root.getChildren().add(0, new TreeItem<>("added"));
        Toolkit.getToolkit().firePulse();
        assertNull(tree.getEditingCell(), "sanity: editing terminated on items modification");
        assertEquals(1, events.size(), "column must have received editCancel");
        assertEquals(editingPosition, events.get(0).getTreeTablePosition(), "editing location of cancel event");
    }

    /**
     * Test that removing the editing item implicitly cancels an ongoing
     * edit and fires a correct cancel event.
     */
    @Test
    public void testEditCancelEventAfterRemoveEditingItem() {
        setupForEditing();
        stageLoader = new StageLoader(tree);
        int editingIndex = 1;
        tree.edit(editingIndex, editingColumn);
        TreeTablePosition<?, ?> editingPosition = tree.getEditingCell();
        List<CellEditEvent<?, ?>> events = new ArrayList<>();
        editingColumn.setOnEditCancel(events::add);
        root.getChildren().remove(editingIndex - 1);
        Toolkit.getToolkit().firePulse();
        assertNull(tree.getEditingCell(), "sanity: editing terminated on items modification");
        assertEquals(1, events.size(), "column must have received editCancel");
        assertEquals(editingPosition, events.get(0).getTreeTablePosition(), "editing location of cancel event");
    }

    /**
     * Test that removing the editing item does not cause a memory leak.
     */
    @Test
    public void testEditCancelMemoryLeakAfterRemoveEditingItem() {
        setupForEditing();
        stageLoader = new StageLoader(tree);
        // the item to test for being gc'ed
        TreeItem<String> editingItem = new TreeItem<>("added");
        WeakReference<TreeItem<?>> itemRef = new WeakReference<>(editingItem);
        root.getChildren().add(0, editingItem);
        Toolkit.getToolkit().firePulse();
        int editingIndex = tree.getRow(editingItem);
        tree.edit(editingIndex, editingColumn);
        root.getChildren().remove(editingItem);
        Toolkit.getToolkit().firePulse();
        editingItem = null;
        attemptGC(itemRef);
        assertEquals(null, itemRef.get(), "treeItem must be gc'ed");
    }

    @Test
    public void testEditStartEventAfterStartOnCell() {
        setupForEditing();
        int editingIndex = 1;
        cell.updateIndex(editingIndex);
        List<CellEditEvent<?, ?>> events = new ArrayList<>();
        editingColumn.setOnEditStart(events::add);
        cell.startEdit();
        assertEquals(editingColumn, events.get(0).getTableColumn());
        TreeTablePosition<?, ?> editingCell = events.get(0).getTreeTablePosition();
        assertEquals(editingIndex, editingCell.getRow());
    }

    @Test
    public void testEditStartEventAfterStartOnTable() {
        setupForEditing();
        int editingIndex = 1;
        cell.updateIndex(editingIndex);
        List<CellEditEvent<?, ?>> events = new ArrayList<>();
        editingColumn.setOnEditStart(events::add);
        tree.edit(editingIndex, editingColumn);
        assertEquals(editingColumn, events.get(0).getTableColumn());
        TreeTablePosition<?, ?> editingCell = events.get(0).getTreeTablePosition();
        assertEquals(editingIndex, editingCell.getRow());
    }

 //------------- commitEdit

    @Test
    public void testCommitEditMustNotFireCancel() {
        setupForEditing();
        // JDK-8187307: handler that resets control's editing state
        editingColumn.setOnEditCommit(e -> {
            TreeItem<String> treeItem = tree.getTreeItem(e.getTreeTablePosition().getRow());
            treeItem.setValue(e.getNewValue());
            tree.edit(-1, null);
        });
        int editingRow = 1;
        cell.updateIndex(editingRow);
        tree.edit(editingRow, editingColumn);
        List<CellEditEvent<?, ?>> events = new ArrayList<>();
        editingColumn.setOnEditCancel(events::add);
        String value = "edited";
        cell.commitEdit(value);
        assertEquals(value, tree.getTreeItem(editingRow).getValue(), "sanity: value committed");
        assertEquals(0, events.size(), "commit must not have fired editCancel");
    }

// fix of JDK-8271474 changed the implementation of how the editing location is evaluated

     @Test
     public void testEditCommitEvent() {
         setupForEditing();
         int editingIndex = 1;
         cell.updateIndex(editingIndex);
         cell.startEdit();
         TreeTablePosition<?, ?> editingPosition = tree.getEditingCell();
         List<CellEditEvent<?, ?>> events = new ArrayList<>();
         editingColumn.setOnEditCommit(events::add);
         cell.commitEdit("edited");
         assertEquals(1, events.size(), "column must have received editCommit");
         assertEquals(editingPosition, events.get(0).getTreeTablePosition(), "editing location of commit event must be same as table's editingCell");
     }

     @Test
     public void testEditCommitEditingCellAtStartEdit() {
         setupForEditing();
         int editingIndex = 1;
         cell.updateIndex(editingIndex);
         cell.startEdit();
         TreeTablePosition<?, ?> editingCellAtStartEdit = TreeTableCellShim.getEditingCellAtStartEdit(cell);
         List<CellEditEvent<?, ?>> events = new ArrayList<>();
         editingColumn.setOnEditCommit(events::add);
         cell.commitEdit("edited");
         assertEquals(1, events.size(), "column must have received editCommit");
         assertEquals(editingCellAtStartEdit, events.get(0).getTreeTablePosition(), "editing location of commit event must be same as editingCellAtStartEdit");
     }

     @Test
     public void testEditCommitEventNullTable() {
         setupForcedEditing(null, editingColumn);
         cell.startEdit();
         TreeTablePosition<?, ?> editingCellAtStartEdit = TreeTableCellShim.getEditingCellAtStartEdit(cell);
         List<CellEditEvent<?, ?>> events = new ArrayList<>();
         editingColumn.addEventHandler(TreeTableColumn.editAnyEvent(), events::add);
         cell.commitEdit("edited");
         assertEquals(1, events.size(), "column must have received editCommit");
         assertEquals(editingCellAtStartEdit, events.get(0).getTreeTablePosition(), "editing location of commit event must be same as editingCellAtStartEdit");
     }

// --- JDK-8271474: implement consistent event firing pattern
//  test pattern:
//        for every edit method
//        for every combinations of null table and null column
//           must not throw NPE
//           expected event state (if applicable)

     @Test
     public void testEditStartNullTable() {
         setupForcedEditing(null, editingColumn);
         List<CellEditEvent<?, ?>> events = new ArrayList<>();
         editingColumn.addEventHandler(TreeTableColumn.editAnyEvent(), events::add);
         cell.startEdit();
         assertEquals(1, events.size());
     }

     @Test
     public void testEditCancelNullTable() {
         setupForcedEditing(null, editingColumn);
         cell.startEdit();
         List<CellEditEvent<?, ?>> events = new ArrayList<>();
         editingColumn.addEventHandler(TreeTableColumn.editAnyEvent(), events::add);
         cell.cancelEdit();
         assertEquals(1, events.size());
     }

     @Test
     public void testEditCommitNullTable() {
         setupForcedEditing(null, editingColumn);
         cell.startEdit();
         List<CellEditEvent<?, ?>> events = new ArrayList<>();
         editingColumn.addEventHandler(TreeTableColumn.editAnyEvent(), events::add);
         cell.commitEdit("edited");
         assertEquals(1, events.size());
     }

     @Test
     public void testEditStartNullColumn() {
         setupForcedEditing(tree, null);
         cell.startEdit();
     }

     @Test
     public void testEditCancelNullColumn() {
         setupForcedEditing(tree, null);
         cell.startEdit();
         cell.cancelEdit();
     }

     @Test
     public void testEditCommitNullColumn() {
         setupForcedEditing(tree, null);
         cell.startEdit();
         cell.commitEdit("edited");
     }

     @Test
     public void testEditStartNullTableNullColumn() {
         setupForcedEditing(null, null);
         cell.startEdit();
     }

     @Test
     public void testEditCancelNullTableNullColumn() {
         setupForcedEditing(null, null);
         cell.startEdit();
         cell.cancelEdit();
     }

     @Test
     public void testEditCommitNullTableNullColumn() {
         setupForcedEditing(null, null);
         cell.startEdit();
         cell.commitEdit("edited");
     }

     @Test
     public void testStartEditOffRangeMustNotFireStartEdit() {
         setupForEditing();
         cell.updateIndex(tree.getExpandedItemCount());
         List<CellEditEvent<?, ?>> events = new ArrayList<>();
         editingColumn.addEventHandler(TreeTableColumn.editStartEvent(), events::add);
         cell.startEdit();
         assertFalse(cell.isEditing(), "sanity: off-range cell must not be editing");
         assertEquals(0, events.size(), "cell must not fire editStart if not editing");
     }

     @Test
     public void testStartEditOffRangeMustNotUpdateEditingLocation() {
         setupForEditing();
         cell.updateIndex(tree.getExpandedItemCount());
         cell.startEdit();
         assertFalse(cell.isEditing(), "sanity: off-range cell must not be editing");
         assertNull(tree.getEditingCell(), "treetable editing location must not be updated");
     }


 //--------- test the test setup

     @Test
     public void testCellStartEditNullTable() {
         setupForcedEditing(null, editingColumn);
         // must not be empty to be switched into editing
         assertFalse(cell.isEmpty());
         cell.startEdit();
         assertTrue(cell.isEditing());
     }

     @Test
     public void testCellStartEditNullColumn() {
         setupForcedEditing(tree, null);
         // must not be empty to be switched into editing
         assertFalse(cell.isEmpty());
         cell.startEdit();
         assertTrue(cell.isEditing());
     }

     @Test
     public void testCellStartEditNullTableNullColumn() {
         setupForcedEditing(null, null);
         // must not be empty to be switched into editing
         assertFalse(cell.isEmpty());
         cell.startEdit();
         assertTrue(cell.isEditing());
     }

     /**
      * Configures the cell to be editable without table or column.
      */
     private void setupForcedEditing(TreeTableView table, TreeTableColumn editingColumn) {
         if (table != null) {
             table.setEditable(true);
             cell.updateTreeTableView(table);
         }
         if (editingColumn != null ) cell.updateTableColumn(editingColumn);
         // force into editable state (not empty)
         cell.setLockItemOnStartEdit(true);
         CellShim.updateItem(cell, "something", false);
     }


    /**
     * Test that cell.cancelEdit can switch table editing off
     * even if a subclass violates its contract.
     *
     * For details, see https://bugs.openjdk.org/browse/JDK-8265206
     *
     */
    @Test
    public void testMisbehavingCancelEditTerminatesEdit() {
        // setup for editing
        TreeTableCell<String, String> cell = new MisbehavingOnCancelTreeTableCell<>();
        tree.setEditable(true);
        TreeTableColumn<String, String> editingColumn = new TreeTableColumn<>("TEST");
        editingColumn.setCellValueFactory(param -> null);
        tree.getColumns().add(editingColumn);
        cell.updateTreeTableView(tree);
        cell.updateTableColumn(editingColumn);
        // test editing: first round
        // switch cell off editing by table api
        int editingIndex = 1;
        int intermediate = 0;
        cell.updateIndex(editingIndex);
        tree.edit(editingIndex, editingColumn);
        assertTrue(cell.isEditing(), "sanity: ");
        try {
            tree.edit(intermediate, editingColumn);
        } catch (Exception ex) {
            // catching to test in finally
        } finally {
            assertFalse(cell.isEditing(), "cell must not be editing");
            assertEquals(intermediate, tree.getEditingCell().getRow(), "table must be editing at intermediate index");
        }
        // test editing: second round
        // switch cell off editing by cell api
        tree.edit(editingIndex, editingColumn);
        assertTrue(cell.isEditing(), "sanity: ");
        try {
            cell.cancelEdit();
        } catch (Exception ex) {
            // catching to test in finally
        } finally {
            assertFalse(cell.isEditing(), "cell must not be editing");
            assertNull(tree.getEditingCell(), "table editing must be cancelled by cell");
        }
    }

    /**
     * See also: <a href="https://bugs.openjdk.org/browse/JDK-8187314">JDK-8187314</a>.
     */
    @Test
    public void testEditCommitValueChangeIsReflectedInCell() {
        setupForEditing();
        editingColumn.setCellValueFactory(cc -> new SimpleObjectProperty<>(cc.getValue().getValue()));
        editingColumn.setOnEditCommit(event -> {
            assertEquals("ABCDEF", event.getNewValue());
            // Change the underlying item.
            root.setValue("ABCDEF [Changed]");
        });

        cell.updateIndex(0);

        assertEquals("Root", cell.getItem());

        cell.startEdit();
        cell.commitEdit("ABCDEF");

        assertEquals("ABCDEF [Changed]", cell.getItem());
    }

    /**
     * Same index and underlying item should not cause the updateItem(..) method to be called.
     */
    @Test
    public void testSameIndexAndItemShouldNotUpdateItem() {
        AtomicInteger counter = new AtomicInteger();

        editingColumn.setCellFactory(view -> new TreeTableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                counter.incrementAndGet();
                super.updateItem(item, empty);
            }
        });
        setupForEditing();

        stageLoader = new StageLoader(tree);

        counter.set(0);
        IndexedCell<String> cell = VirtualFlowTestUtils.getCell(tree, 0, 0);
        cell.updateIndex(0);

        assertEquals(0, counter.get());
    }

    /**
     * The contract of a {@link TreeTableCell} is that isItemChanged(..)
     * is called when the index is 'changed' to the same number as the old one, to evaluate if we need to call
     * updateItem(..).
     */
    @Test
    public void testSameIndexIsItemsChangedShouldBeCalled() {
        AtomicBoolean isItemChangedCalled = new AtomicBoolean();

        editingColumn.setCellFactory(view -> new TreeTableCell<>() {
            @Override
            protected boolean isItemChanged(String oldItem, String newItem) {
                isItemChangedCalled.set(true);
                return super.isItemChanged(oldItem, newItem);
            }
        });
        setupForEditing();

        stageLoader = new StageLoader(tree);

        IndexedCell<String> cell = VirtualFlowTestUtils.getCell(tree, 0, 0);
        cell.updateIndex(0);

        assertTrue(isItemChangedCalled.get());
    }

    public static class MisbehavingOnCancelTreeTableCell<S, T> extends TreeTableCell<S, T> {

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            throw new RuntimeException("violating contract");
        }
    }
}
