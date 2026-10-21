package com.loxs.core;

import com.loxs.model.Finding;

public interface ScanListener {
    void log(String message);

    void finding(Finding finding);

    void progress(int completed, int total);

    boolean cancelled();
}
