package io.metamorphic.fileservices;

import java.util.Date;

/**
 * Created by markmo on 18/05/15.
 */
public class ParsedDate {

    private Date date;
    private String format;

    ParsedDate(Date date, String format) {
        this.date = date;
        this.format = format;
    }

    public Date getDate() {
        return date;
    }

    public String getFormat() {
        return format;
    }
}
