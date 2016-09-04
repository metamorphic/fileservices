package io.metamorphic.fileservices;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Created by markmo on 4/07/2015.
 */
public class DateParser implements ITypeParser<ParsedDate> {

    // Overwritten by 'date-formats' setting if provided
    private static String[] dateFormats = new String[] {
            // pattern                      // example
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",   // 2001-07-04T12:08:56.235-0700
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", // 2001-07-04T12:08:56.235-07:00
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS", // 2001-07-04T12:08:56.235000
            "yyyy-MM-dd HH:mm:ss.SSSZ",     // 2001-07-04 12:08:56.235-0700
            "yyyy-MM-dd HH:mm:ss.SSSXXX",   // 2001-07-04 12:08:56.235-07:00
            "yyyy-MM-dd HH:mm:ss.SSSSSS",   // 2001-07-04 12:08:56.235000
            "yyyyMMdd HH:mm:ss",            // 20010704 12:08:56
            "EEE, MMM d, ''yy",             // Wed, Jul 4, '01
            "EEE, MMM d, yyyy",             // Wed, Jul 4, 2001
            "yyyy.MM.dd",                   // 2001.07.04
            "yyyy-MM-dd",                   // 2001-07-04
            "yyyy/MM/dd",                   // 2001/07/04
            "dd.MM.yyyy",                   // 04.07.2001
            "dd-MM-yyyy",                   // 04-07-2001
            "dd/MM/yyyy",                   // 04/07/2001
            "MM.dd.yyyy",                   // 07.04.2001
            "MM-dd-yyyy",                   // 07-04-2001
            "MM/dd/yyyy",                   // 07/04/2001
            "dd.MM.yy",                     // 04.07.01
            "dd-MM-yy",                     // 04-07-01
            "dd/MM/yy",                     // 04/07/01
            "MM.dd.yy",                     // 07.04.01
            "MM-dd-yy",                     // 07-04-01
            "MM/dd/yy",                     // 07/04/01
            "dd/MMM/yy",                    // 03/APR/15
            "yyyy-MM-dd",
            "yyyy-MM-dd'T'HH",
            "yyyy-MM-dd HH",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss Z",
            "yyyy-MM-dd HH:mm:ss Z"
    };

    private List<String> dfList;

    public DateParser() {
        this.dfList = Arrays.asList(dateFormats);
    }

    public DateParser(String[] dateFormats) {
        this.dfList = Arrays.asList(dateFormats);
    }

    @Override
    public ParsedDate parse(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        int i = 0;
        for (String format : dfList) {
            try {
                DateFormat df = new SimpleDateFormat(format);
                Date dt = df.parse(v);

                // Move valid format to top of list
                if (i > 0) {
                    dfList = rearrange(dfList, format);
                }

                return new ParsedDate(dt, format);

            } catch (ParseException e) {
                // ignore
            }
            i += 1;
        }
        return null;
    }

    private static <T> List<T> rearrange(List<T> items, T input) {
        int index = items.indexOf(input);
        List<T> copy;
        if (index >= 0) {
            copy = new ArrayList<T>(items.size());
            copy.addAll(items.subList(0, index));
            copy.add(0, items.get(index));
            copy.addAll(items.subList(index + 1, items.size()));
        } else {
            copy = new ArrayList<T>(items);
        }
        return copy;
    }
}
