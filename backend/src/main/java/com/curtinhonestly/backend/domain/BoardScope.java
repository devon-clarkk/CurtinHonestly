package com.curtinhonestly.backend.domain;

/**
 * Which board a thread lives on. GENERAL is the single site-wide board;
 * UNIT threads belong to exactly one unit and carry a non-null unit reference.
 */
public enum BoardScope {
    GENERAL,
    UNIT
}
