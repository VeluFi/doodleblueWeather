package com.example.weatherdoodleblue.utils;

import java.util.Locale;

public class Language {

    public static String getOwmLanguage() {
        String language = Locale.getDefault().getLanguage();


        return language;

    }

}
