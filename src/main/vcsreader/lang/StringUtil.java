package vcsreader.lang;

import java.util.ArrayList;
import java.util.List;

public class StringUtil {
    public static List<String> split(String s, String separator) {
        List<String> result = new ArrayList<String>();
        int lastIndex = 0;
        int index = s.indexOf(separator);

        while (index != -1) {
            String substring = s.substring(lastIndex, index);
            if (!substring.equals(separator) && index > 0)
                result.add(substring);

            lastIndex = index + separator.length();
            index = s.indexOf(separator, lastIndex);
        }
        if (lastIndex < s.length())
            result.add(s.substring(lastIndex, s.length()));

        return result;
    }

    public static String trim(String s, String chars) {
        int start = 0;
        int end = s.length();
        while (start < end && chars.indexOf(s.charAt(start)) != -1) start++;
        while (end > start && chars.indexOf(s.charAt(end - 1)) != -1) end--;
        return s.substring(start, end);
    }
}
