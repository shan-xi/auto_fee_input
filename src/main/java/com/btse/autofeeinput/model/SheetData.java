package com.btse.autofeeinput.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the headers and data rows of the uploaded sheet.
 * Rows are ObservableList<String> so they bind directly to TableView.
 */
public class SheetData {

    private final List<String> headers = new ArrayList<>();
    private final ObservableList<ObservableList<String>> rows = FXCollections.observableArrayList();

    public List<String> getHeaders() {
        return headers;
    }

    public ObservableList<ObservableList<String>> getRows() {
        return rows;
    }

    public void clear() {
        headers.clear();
        rows.clear();
    }

    public int columnIndex(String header) {
        return headers.indexOf(header);
    }

    public String getCell(int rowIndex, int colIndex) {
        ObservableList<String> r = rows.get(rowIndex);
        return colIndex < r.size() ? r.get(colIndex) : "";
    }

    public void setCell(int rowIndex, int colIndex, String value) {
        ObservableList<String> r = rows.get(rowIndex);
        while (r.size() <= colIndex) {
            r.add("");
        }
        r.set(colIndex, value);
    }
}
