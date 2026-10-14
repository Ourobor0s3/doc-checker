package com.example.docchecker.pd.model;

/** Фрагмент содержимого файла с адресом, по которому его можно найти. */
public record TextUnit(String locator, String text, String hint, TextKind kind) {}
