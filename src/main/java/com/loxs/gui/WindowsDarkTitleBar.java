package com.loxs.gui;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.concurrent.atomic.AtomicReference;

public final class WindowsDarkTitleBar {
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19;
    private static final int DWMWA_BORDER_COLOR = 34;
    private static final int DWMWA_CAPTION_COLOR = 35;
    private static final int DWMWA_TEXT_COLOR = 36;

    private WindowsDarkTitleBar() {
    }

    public static void apply(Stage stage) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return;
        }
        stage.setOnShown(event -> {
            // İlk karede beyaz başlık çubuğu görünmesin diye hemen uygula,
            // ardından pencere tam oturunca bir kez daha doğrula.
            applyNow(stage.getTitle());
            new Thread(() -> {
                try {
                    Thread.sleep(120);
                    Platform.runLater(() -> applyNow(stage.getTitle()));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }, "loxs-dark-titlebar").start();
        });
    }

    private static void applyNow(String title) {
        try {
            HWND hwnd = findWindow(title);
            if (hwnd == null) {
                return;
            }
            IntByReference enabled = new IntByReference(1);
            Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE,
                    enabled.getPointer(), 4);
            Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_OLD,
                    enabled.getPointer(), 4);

            setColor(hwnd, DWMWA_CAPTION_COLOR, 0x0B1120);
            setColor(hwnd, DWMWA_TEXT_COLOR, 0xE5EDF8);
            setColor(hwnd, DWMWA_BORDER_COLOR, 0x263449);
        } catch (Throwable ignored) {
            // Eski Windows sürümlerinde DWM başlık rengi desteklenmeyebilir.
        }
    }

    private static HWND findWindow(String title) {
        HWND direct = User32.INSTANCE.FindWindow(null, title);
        if (direct != null) {
            return direct;
        }
        long pid = ProcessHandle.current().pid();
        AtomicReference<HWND> found = new AtomicReference<>();
        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            IntByReference processId = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, processId);
            if (processId.getValue() == pid) {
                char[] buffer = new char[512];
                User32.INSTANCE.GetWindowText(hwnd, buffer, buffer.length);
                String text = Native.toString(buffer);
                if (text.startsWith("LOXS")) {
                    found.set(hwnd);
                    return false;
                }
            }
            return true;
        }, null);
        return found.get();
    }

    private static void setColor(HWND hwnd, int attribute, int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        int colorRef = red | (green << 8) | (blue << 16);
        IntByReference value = new IntByReference(colorRef);
        Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, attribute, value.getPointer(), 4);
    }

    private interface Dwmapi extends Library {
        Dwmapi INSTANCE = Native.load("dwmapi", Dwmapi.class);

        int DwmSetWindowAttribute(HWND hwnd, int attribute, Pointer value, int size);
    }
}
