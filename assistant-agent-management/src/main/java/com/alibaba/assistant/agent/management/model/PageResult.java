package com.alibaba.assistant.agent.management.model;

import java.util.List;

public class PageResult<T> {

    private List<T> data;
    private long total;
    private int page;
    private int size;

    public PageResult() {
    }

    public PageResult(List<T> data, long total, int page, int size) {
        this.data = data;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public static <T> PageResult<T> of(List<T> data, long total, int page, int size) {
        return new PageResult<>(data, total, page, size);
    }

    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
