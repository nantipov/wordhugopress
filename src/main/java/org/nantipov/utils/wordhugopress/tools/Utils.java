package org.nantipov.utils.wordhugopress.tools;

import java.util.Optional;

public class Utils {

    private Utils() {

    }

    public static <T> Optional<T> orElseOptional(Optional<T> optional1, Optional<T> optional2) {
        if (optional1.isPresent()) {
            return optional1;
        } else {
            return optional2;
        }
    }
}
