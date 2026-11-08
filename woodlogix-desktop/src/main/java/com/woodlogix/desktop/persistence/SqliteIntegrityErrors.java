package com.woodlogix.desktop.persistence;

import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

public final class SqliteIntegrityErrors {
    private SqliteIntegrityErrors() {
    }

    public static boolean isForeignKeyViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLiteException sqliteException
                    && sqliteException.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_FOREIGNKEY) {
                return true;
            }
        }
        return false;
    }
}
