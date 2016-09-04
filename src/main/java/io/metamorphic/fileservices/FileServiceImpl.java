package io.metamorphic.fileservices;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.metamorphic.commons.Pair;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.metamorphic.commons.utils.StringUtils.*;

/**
 * Created by markmo on 18/05/15.
 */
public class FileServiceImpl implements FileService {

    private static final Log log = LogFactory.getLog(FileServiceImpl.class);

    private static final char[] preferredColumnDelimiters = new char[] { ',', '\t', ';', ' ', '|' };

    // row delimiters tested for
    private static final String[] lineEndings = new String[] { "\n", "\r\n", "\r", "<ret>" };

    // to resolve ambiguity when a value qualifies for more than one type
    private static final List<ValueTypes> typeHierarchy = Arrays.asList(
        ValueTypes.NONE,
        ValueTypes.BIT,
        ValueTypes.BOOLEAN,
        ValueTypes.INTEGER,
        ValueTypes.NUMERIC,
        ValueTypes.DATE,
        ValueTypes.STRING
    );

    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^\\s*[+-]?(0(?=\\.)|[1-9])[0-9]*(\\.[0-9]+)?\\s*$");

    private TypeParser typeParser;

    public FileServiceImpl() {
        typeParser = new TypeParser();
        typeParser.registerTypeParser(Boolean.class, new BooleanParser());
        typeParser.registerTypeParser(ParsedDate.class, new DateParser());
    }

    public void setDateFormats(String[] dateFormats) {
        typeParser.registerTypeParser(ParsedDate.class, new DateParser(dateFormats));
    }

    public FileParameters sniff(String data, String lineEnding) {
        if (log.isDebugEnabled()) {
            log.debug("Guessing text qualifier and delimiter");
        }
        FileParameters params1 = guessQuoteAndDelimiter(data, lineEnding);
        String guessedDelimiter = params1.getColumnDelimiter();
        if (log.isDebugEnabled() && !guessedDelimiter.isEmpty()) {
            log.debug("delimiter is [" + StringEscapeUtils.escapeJava(guessedDelimiter) + "](" +
                    (int) guessedDelimiter.charAt(0) + ") (length=" +
                    guessedDelimiter.length() + ")");
        }
        if (guessedDelimiter.isEmpty() || guessedDelimiter.charAt(0) == 0) {
            FileParameters params2 = guessDelimiter(data, lineEnding);
            if (params2.getColumnDelimiter().isEmpty()) {
                // TODO
                // limit to 20 lines
                FileParameters params3 = findMultiCharSequences(data, lineEnding);
                if (params3.getColumnDelimiter().isEmpty()) {
                    log.warn("Could not determine delimiter - returning null");
                    return null;
                }
                params1.setColumnDelimiter(params3.getColumnDelimiter());
                params1.setSkipInitialSpace(params3.isSkipInitialSpace());
            }
            params1.setColumnDelimiter(params2.getColumnDelimiter());
            params1.setSkipInitialSpace(params2.isSkipInitialSpace());
        }
        return params1;
    }

    public TypeInfo deduceDataType(String value) {
        if (value == null) return new TypeInfo(ValueTypes.NONE);
        String v = value.trim();
        if (v.isEmpty()) return new TypeInfo(ValueTypes.NONE);
        Matcher m = NUMERIC_PATTERN.matcher(value);
        try {
            Integer i = Integer.parseInt(v, 10);
            // check that the decimal place is not truncated
            if (i.toString().equals(v)) {
                if (i == 0 || i == 1) {
                    return new TypeInfo(ValueTypes.BIT);
                } else {
                    return new TypeInfo(ValueTypes.INTEGER);
                }
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        if (m.matches()) {
            try {
                Double.parseDouble(v);
                return new TypeInfo(ValueTypes.NUMERIC);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        ParsedDate dt = typeParser.parse(v, ParsedDate.class);
        if (dt != null) return new TypeInfo(ValueTypes.DATE, "format", dt.getFormat());
        Boolean bool = typeParser.parse(v, Boolean.class);
        if (bool != null) return new TypeInfo(ValueTypes.BOOLEAN);
        if (v.length() > 128) return new TypeInfo(ValueTypes.TEXT);
        return new TypeInfo(ValueTypes.STRING);
    }

    /**
     * urrgh.. the following is dense. I'd use Java 8 if I could be sure of
     * compatibility, or Scala if I could.
     *
     * @param data
     * @return
     */
    public FileParameters findMultiCharSequences(String data, String lineEnding) {
        String[] rows = data.split(lineEnding);
        int chunkLength = Math.min(10, rows.length);
        int iteration = 0;
        Map<String, Map<Integer, Integer>> strFrequency = new HashMap<>();
        Map<String, Integer[]> modes = new HashMap<>();
        Map<String, Integer[]> delims = new HashMap<>();
        String delim;
        boolean skipInitialSpace;
        int start = 0;
        int end = Math.min(chunkLength, rows.length);
        int slidingWindow = 5;
        ObjectMapper mapper = new ObjectMapper();
        while (start < rows.length) {
            iteration += 1;
            for (String line : Arrays.asList(rows).subList(start, end)) {
                Map<String, Integer> counts = new HashMap<>();
                for (int w = 2; w <= slidingWindow; w++) {
                    for (int i = 0; i <= line.length() - w; i++) {
                        String str = line.substring(i, i + w);
                        if (counts.containsKey(str)) {
                            counts.put(str, counts.get(str) + 1);
                        } else {
                            counts.put(str, 1);
                        }
                    }
                }
                for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                    if (entry.getValue() > 1) {
                        Map<Integer, Integer> metaFrequency;
                        if (strFrequency.containsKey(entry.getKey())) {
                            metaFrequency = strFrequency.get(entry.getKey());
                        } else {
                            metaFrequency = new HashMap<>();
                        }
                        int value = 0;
                        if (metaFrequency.containsKey(entry.getValue())) {
                            value = metaFrequency.get(entry.getValue());
                        }
                        metaFrequency.put(entry.getValue(), value + 1);
                        //Map<Integer, Integer> metaFrequency = strFrequency.getOrDefault(entry.getKey(), new HashMap<Integer, Integer>());
                        //metaFrequency.put(entry.getValue(), metaFrequency.getOrDefault(entry.getValue(), 0) + 1);
                        strFrequency.put(entry.getKey(), metaFrequency);
                    }
                }
            }
            for (Map.Entry<String, Map<Integer, Integer>> entry : strFrequency.entrySet()) {
                Map<Integer, Integer> metaFrequency = entry.getValue();
                if (!(metaFrequency.size() == 1 && metaFrequency.containsKey(0))) {
                    if (metaFrequency.size() > 0) {
                        Map.Entry<Integer, Integer> maxItem = null;
                        for (Map.Entry<Integer, Integer> item : metaFrequency.entrySet()) {
                            if (maxItem == null || item.getValue() > maxItem.getValue()) {
                                maxItem = item;
                            }
                        }
                        int sumOtherFreqs = 0;
                        Integer maxFreq = (maxItem == null ? null : maxItem.getKey());
                        Integer maxFreqVal = (maxItem == null ? 0 : maxItem.getValue());
                        for (Map.Entry<Integer, Integer> item : metaFrequency.entrySet()) {
                            if (!item.getKey().equals(maxFreq)) {
                                sumOtherFreqs += item.getValue();
                            }
                        }
                        modes.put(entry.getKey(), new Integer[] { maxFreq, maxFreqVal - sumOtherFreqs });
                    } else {
                        Map.Entry<Integer, Integer> it = metaFrequency.entrySet().iterator().next();
                        modes.put(entry.getKey(), new Integer[] { it.getKey(), it.getValue() });
                    }
                }
            }
            int total = chunkLength * iteration;
            if (log.isDebugEnabled()) {
                try {
                    log.debug(mapper.writeValueAsString(modes));
                } catch (JsonProcessingException e) {
                    // ignore
                }
            }
            // (rows of consistent data) / (number of rows) = 100%
            double consistency = 1.0;

            // minimum consistency threshold
            double threshold = 0.9;

            while (delims.isEmpty() && consistency >= threshold) {
                for (Map.Entry<String, Integer[]> entry : modes.entrySet()) {
                    Integer[] v = entry.getValue();
                    if (v[0] > 0 && v[1] > 0) {
                        if ((v[1] / total) >= consistency) {
                            delims.put(entry.getKey(), v);
                        }
                    }
                }
                consistency -= 0.01;
            }
            if (delims.size() == 1) {
                delim = delims.keySet().iterator().next();
                String firstLine = rows[0];
                int delimCount = countSubstring(firstLine, delim);
                Pattern p = Pattern.compile(delim + " ");
                Matcher m = p.matcher(firstLine);
                int delimWithSpaceCount = 0;
                while (m.find()) delimWithSpaceCount++;
                skipInitialSpace = (delimCount == delimWithSpaceCount);
                return new FileParameters(delim, skipInitialSpace);
            }

            // analyze another chunkLength lines
            start = end;
            end += chunkLength;
            end = Math.min(end, rows.length);
        }
        if (delims.isEmpty()) {
            return new FileParameters();
        }

        // if there's more than one, fall back to a 'preferred' list
        for (Character ch : preferredColumnDelimiters) {
            String del = ch.toString();
            if (delims.keySet().contains(del)) {
                String firstLine = rows[0];
                int delimCount = countSubstring(firstLine, del);
                Pattern p = Pattern.compile(del + " ");
                Matcher m = p.matcher(firstLine);
                int delimWithSpaceCount = 0;
                while (m.find()) delimWithSpaceCount++;
                skipInitialSpace = (delimCount == delimWithSpaceCount);
                return new FileParameters(del, skipInitialSpace);
            }
        }
        if (log.isDebugEnabled()) {
            try {
                log.debug(mapper.writeValueAsString(delims));
            } catch (JsonProcessingException e) {
                // ignore
            }
        }

        // nothing else indicates a preference, pick the character that
        // dominates(?)
        Map.Entry<String, Integer[]> maxEntry = null;
        for (Map.Entry<String, Integer[]> entry : delims.entrySet()) {
            if (maxEntry == null || entry.getValue()[0] > maxEntry.getValue()[0]) {
                maxEntry = entry;
            }
        }
        delim = maxEntry.getKey();
        String firstLine = rows[0];
        int delimCount = countSubstring(firstLine, delim);
        Pattern p = Pattern.compile(delim + " ");
        Matcher m = p.matcher(firstLine);
        int delimWithSpaceCount = 0;
        while (m.find()) delimWithSpaceCount++;
        skipInitialSpace = (delimCount == delimWithSpaceCount);
        return new FileParameters(delim, skipInitialSpace);
    }

    /**
     * The delimiter /should/ occur the same number of times on each row.
     * However, due to malformed data, it may not. We don't want an all
     * or nothing approach, so we allow for small variations in this number.
     *
     *   1) build a table of the frequency of each character on every line.
     *   2) build a table of frequencies of this frequency (meta-frequency?),
     *      e.g. 'x occurred 5 times in 10 rows, 6 times in 1000 rows,
     *      7 times in 2 rows'
     *   3) use the mode of the meta-frequency to determine the /expected/
     *      frequency for that character
     *   4) find out how often the character actually meets that goal
     *   5) the character that best meets its goal is the delimiter
     *      For performance reasons, the data is evaluated in chunks, so it can
     *      try and evaluate the smallest portion of the data possible, evaluating
     *      additional chunks as necessary.
     *
     * @param data File data
     * @return metastore.models.FileParameters
     */
    public FileParameters guessDelimiter(String data, String lineEnding) {
        ObjectMapper mapper = new ObjectMapper();
        String[] rows = data.split(lineEnding);
        // Check in the two-byte UTF8 range
        Character[] cs = new Character[2048];
        for (int i = 0; i < 2048; i++) {
            cs[i] = (char)i;
        }

        int chunkLength = Math.min(10, rows.length);
        if (log.isDebugEnabled()) {
            log.debug("rows.length " + rows.length);
            log.debug("chunkLength " + chunkLength);
        }
        int iteration = 0;
        Map<Character, Map<Integer, Integer>> charFrequency = new HashMap<>();
        Map<Character, Integer[]> modes = new HashMap<>();
        Map<Character, Integer[]> delims = new HashMap<>();
        Character delim;
        boolean skipInitialSpace;
        int start = 0;
        int end = Math.min(chunkLength, rows.length);
        while (start < rows.length) {
            iteration += 1;
            for (String line : Arrays.asList(rows).subList(start, end)) {
                //for (Character ch : ascii) {
                for (Character ch : cs) {
                    Map<Integer, Integer> metaFrequency;
                    if (charFrequency.containsKey(ch)) {
                        metaFrequency = charFrequency.get(ch);
                    } else {
                        metaFrequency = new HashMap<>();
                    }
                    int freq = 0;
                    for (int i = 0; i < line.length(); i++) {
                        if (line.charAt(i) == ch) freq++;
                    }
                    if (metaFrequency.containsKey(freq)) {
                        metaFrequency.put(freq, metaFrequency.get(freq) + 1);
                    } else {
                        metaFrequency.put(freq, 1);
                    }
                    charFrequency.put(ch, metaFrequency);
                }
            }
            for (Map.Entry<Character, Map<Integer, Integer>> entry : charFrequency.entrySet()) {
                Map<Integer, Integer> metaFrequency = entry.getValue();
                if (!(metaFrequency.size() == 1 && metaFrequency.containsKey(0))) {
                    if (metaFrequency.size() > 0) {
                        Map.Entry<Integer, Integer> maxItem = null;
                        for (Map.Entry<Integer, Integer> item : metaFrequency.entrySet()) {
                            if (maxItem == null || item.getValue() > maxItem.getValue()) {
                                maxItem = item;
                            }
                        }
                        int sumOtherFreqs = 0;
                        Integer maxFreq = (maxItem == null ? null : maxItem.getKey());
                        Integer maxFreqVal = (maxItem == null ? 0 : maxItem.getValue());
                        for (Map.Entry<Integer, Integer> item : metaFrequency.entrySet()) {
                            if (!item.getKey().equals(maxFreq)) {
                                sumOtherFreqs += item.getValue();
                            }
                        }
                        modes.put(entry.getKey(), new Integer[] { maxFreq, maxFreqVal - sumOtherFreqs });
                    } else {
                        Map.Entry<Integer, Integer> it = metaFrequency.entrySet().iterator().next();
                        modes.put(entry.getKey(), new Integer[] { it.getKey(), it.getValue() });
                    }
                }
            }
            //int total = chunkLength * iteration;
            double total = end * iteration;

            if (log.isDebugEnabled()) {
                log.debug("modes:");
                try {
                    log.debug(mapper.writeValueAsString(modes));
                } catch (JsonProcessingException e) {
                    // ignore
                }
            }

            // (rows of consistent data) / (number of rows) = 100%
            double consistency = 1.0;

            // minimum consistency threshold
            double threshold = 0.9;

            while (delims.isEmpty() && consistency >= threshold) {
                for (Map.Entry<Character, Integer[]> entry : modes.entrySet()) {
                    Integer[] v = entry.getValue();
                    if (v[0] > 0 && v[1] > 0) {
                        if (log.isDebugEnabled()) {
                            log.debug(entry.getKey() + " " + v[1] + " / " + total + " = " + (v[1] / total) + " ~ " + consistency);
                        }
                        if ((v[1] / total) >= consistency) {
                            delims.put(entry.getKey(), v);
                        }
                    }
                }
                consistency -= 0.01;
            }
            if (delims.size() == 1) {
                delim = delims.keySet().iterator().next();
                String firstLine = rows[0];
                int delimCount = 0;
                for (int i = 0; i < firstLine.length(); i++) {
                    if (firstLine.charAt(i) == delim) {
                        delimCount += 1;
                    }
                }
                Pattern p = Pattern.compile(delim + " ");
                Matcher m = p.matcher(firstLine);
                int delimWithSpaceCount = 0;
                while (m.find()) delimWithSpaceCount++;
                skipInitialSpace = (delimCount == delimWithSpaceCount);
                return new FileParameters(delim.toString(), skipInitialSpace);
            }

            // analyze another chunkLength lines
            start = end;
            end += chunkLength;
            end = Math.min(end, rows.length);
        }
        if (delims.isEmpty()) {
            return new FileParameters();
        }

        // if there's more than one, fall back to a 'preferred' list
        for (Character ch : preferredColumnDelimiters) {
            if (delims.keySet().contains(ch)) {
                String firstLine = rows[0];
                int delimCount = 0;
                for (int i = 0; i < firstLine.length(); i++) {
                    if (firstLine.charAt(i) == ch) {
                        delimCount += 1;
                    }
                }
                Pattern p = Pattern.compile(ch + " ");
                Matcher m = p.matcher(firstLine);
                int delimWithSpaceCount = 0;
                while (m.find()) delimWithSpaceCount++;
                skipInitialSpace = (delimCount == delimWithSpaceCount);
                return new FileParameters(ch.toString(), skipInitialSpace);
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("delims:");
            try {
                log.debug(mapper.writeValueAsString(delims));
            } catch (JsonProcessingException e) {
                // ignore
            }
        }

        // nothing else indicates a preference, pick the character that
        // dominates(?)
        Map.Entry<Character, Integer[]> maxEntry = null;
        for (Map.Entry<Character, Integer[]> entry : delims.entrySet()) {
            if (maxEntry == null || entry.getValue()[0] > maxEntry.getValue()[0]) {
                maxEntry = entry;
            }
        }
        delim = maxEntry.getKey();
        String firstLine = rows[0];
        int delimCount = 0;
        for (int i = 0; i < firstLine.length(); i++) {
            if (firstLine.charAt(i) == delim) {
                delimCount += 1;
            }
        }
        Pattern p = Pattern.compile(delim + " ");
        Matcher m = p.matcher(firstLine);
        int delimWithSpaceCount = 0;
        while (m.find()) delimWithSpaceCount++;
        skipInitialSpace = (delimCount == delimWithSpaceCount);
        return new FileParameters(delim.toString(), skipInitialSpace);
    }

    /**
     * Looks for text enclosed between two identical quotes (the probable
     * textQualifier) which are preceded and followed by the same character
     * (the probable delimiter).
     * For example:
     *                  ,'some text',
     *
     * The quote with the most wins, same with the delimiter. If there is
     * no textQualifier then the delimiter can't be determined this way.
     *
     * @param data File data
     * @return metastore.models.FileParameters
     */
    public FileParameters guessQuoteAndDelimiter(String data, String lineEnding) {
        String[] regexes = new String[] {
                "(?<delim>[^\\w" + lineEnding + "\"']+)(?<space> ?)(?<quote>[\"']).*?(\\k<quote>)(\\k<delim>)",
                "(?:^|" + lineEnding + ")(?<quote>[\"']).*?(\\k<quote>)(?<delim>[^\\w" + lineEnding + "\"']+)(?<space> ?)",
                "(?:^|" + lineEnding + ")(?<quote>[\"']).*?(\\k<quote>)(?:$|" + lineEnding + ")"
        };

        // embedded construction flags        meanings
        // flags
        // (?i)     Pattern.CASE_INSENSITIVE  Enables case-insensitive matching.
        // (?d)     Pattern.UNIX_LINES        Enables Unix lines mode.
        // (?m)     Pattern.MULTILINE         Enables multi line mode.
        // (?s)     Pattern.DOTALL            Enables "." to match line terminators.
        // (?u)     Pattern.UNICODE_CASE      Enables Unicode-aware case folding.
        // (?x)     Pattern.COMMENTS          Permits white space and comments in the pattern.
        // ---      Pattern.CANON_EQ          Enables canonical equivalence.
        //
        Matcher matches = null;
        boolean matchNotFound = true;
        if (log.isDebugEnabled()) {
            log.debug("Matching quote patterns");
        }
        for (String regex : regexes) {
            Pattern p = Pattern.compile(regex, Pattern.MULTILINE | Pattern.DOTALL);
            matches = p.matcher(data);
            if (matches.find(0)) {
                matchNotFound = false;
                break;
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Match " + (matchNotFound ? "not found" : "found"));
        }
        if (matchNotFound) {
            return new FileParameters();
        }
        Map<String, Integer> quotes = new HashMap<>();
        Map<String, Integer> delims = new HashMap<>();
        int spaces = 0;
        String quote = matches.group("quote");
        if (quote != null) {
            if (quotes.containsKey(quote)) {
                quotes.put(quote, quotes.get(quote) + 1);
            } else {
                quotes.put(quote, 1);
            }
        }
        String delim = matches.group("delim");
        if (delim != null) {
            if (delims.containsKey(delim)) {
                delims.put(delim, delims.get(delim) + 1);
            } else {
                delims.put(delim, 1);
            }
            if (matches.group("space") != null) {
                spaces += 1;
            }
        }
        Map.Entry<String, Integer> maxEntry = null;
        for (Map.Entry<String, Integer> entry : quotes.entrySet()) {
            if (maxEntry == null || entry.getValue() > maxEntry.getValue()) {
                maxEntry = entry;
            }
        }
        String textQualifier = (maxEntry == null ? "" : maxEntry.getKey());
        String columnDelimiter = "";
        boolean skipInitialSpace = false;
        if (!delims.isEmpty()) {
            maxEntry = null;
            for (Map.Entry<String, Integer> entry : delims.entrySet()) {
                if (maxEntry == null || entry.getValue() > maxEntry.getValue()) {
                    maxEntry = entry;
                }
            }
            columnDelimiter = (maxEntry == null ? "" : maxEntry.getKey());
            skipInitialSpace = (delims.get(columnDelimiter) == spaces);
            if (lineEnding.equals(columnDelimiter)) { // most likely a file with a single column
                columnDelimiter = "";

            }
        }
        // if we see an extra quote between delimiters, we've got a
        // double quoted format
        String del = Pattern.quote(columnDelimiter);
        String delFirstChar = Pattern.quote(columnDelimiter.charAt(0) + "");
        String qot = Pattern.quote(textQualifier);
        String dqr = "(?m)((" + del + ")|^)\\W*" + qot + "[^" + delFirstChar + lineEnding + "]*" + qot + "[^" + delFirstChar + lineEnding + "]*" + qot + "\\W*((" + del + ")|$$)";
        Pattern p = Pattern.compile(dqr);
        Matcher m = p.matcher(data);
        boolean doubleQuoted = m.find(0) && (m.group(1) != null);
        return new FileParameters(textQualifier, doubleQuoted, columnDelimiter, skipInitialSpace);
    }

    /**
     * Creates a dictionary of types of data in each column. If any
     * column is of a single type (say, integers), *except* for the first
     * row, then the first row is presumed to be labels. If the type
     * can't be determined, it is assumed to be a string in which case
     * the length of the string is the determining factor: if all of the
     * rows except for the first are the same length, it's a header.
     * Finally, a 'vote' is taken at the end for each column, adding or
     * subtracting from the likelihood of the first row being a header.
     *
     * @param data File data
     * @return boolean
     */
    public boolean hasHeader(String[][] data) {
        String[] header = data[0];
        int lenColumns = header.length;
        Map<Integer, Pair<ValueTypes, Integer>> columnTypes = new HashMap<>();
        for (int i = 0; i < lenColumns; i++) {
            columnTypes.put(i, new Pair<>(ValueTypes.NONE, 0));
        }
        for (String[] row : data) {
            if (row.length == lenColumns) {
                for (int i = 0; i < lenColumns; i++) {
                    Pair<ValueTypes, Integer> thisType = getType(row[i]);
                    if (!thisType.equals(columnTypes.get(i))) {
                        columnTypes.put(i, thisType);
                    } else {
                        columnTypes.put(i, new Pair<>(ValueTypes.NONE, 0));
                    }
                }
            }
        }
        int hasHeaderVote = 0;
        for (Map.Entry<Integer, Pair<ValueTypes, Integer>> entry : columnTypes.entrySet()) {
            hasHeaderVote += testHeaderType(entry.getValue(), header[entry.getKey()]);
        }
        return hasHeaderVote > 0;
    }

    public boolean hasHeader(List<List<String>> sample) {
        int sampleSize = sample.size();
        String[][] data = new String[sampleSize][];
        for (int i = 0; i < sampleSize; i++) {
            List<String> row = sample.get(i);
            data[i] = row.toArray(new String[row.size()]);
        }
        return hasHeader(data);
    }

    private Pair<ValueTypes, Integer> getType(String str) {
        if (str == null) return new Pair<>(ValueTypes.NONE, 0);
        Matcher m = NUMERIC_PATTERN.matcher(str);
        if (m.matches()) {
            try {
                Integer.parseInt(str);
                return new Pair<>(ValueTypes.INTEGER, str.length());
            } catch (NumberFormatException e) {
                // do nothing
            }
            try {
                Double.parseDouble(str);
                return new Pair<>(ValueTypes.NUMERIC, str.length());
            } catch (NumberFormatException e) {
                // do nothing
            }
        }
        ParsedDate dt = typeParser.parse(str, ParsedDate.class);
        if (dt != null) return new Pair<>(ValueTypes.DATE, str.length());

        Boolean bool = typeParser.parse(str, Boolean.class);
        if (bool != null) return new Pair<>(ValueTypes.BOOLEAN, str.length());

        return new Pair<>(ValueTypes.STRING, str.length());
    }

    private int testHeaderType(Pair<ValueTypes, Integer> type, String cell) {
        if (cell == null) return 0;
        if (type.l == ValueTypes.STRING) {
            return (cell.length() == type.r) ? -1 : 1;
        }
        if (type.l == ValueTypes.INTEGER) {
            try {
                Integer i = Integer.parseInt(cell);
                // check that the decimal place is not truncated
                if (i.toString().equals(cell)) {
                    return -1;
                }
            } catch (NumberFormatException e) {
                return 1;
            }
        }
        if (type.l == ValueTypes.NUMERIC) {
            try {
                Double.parseDouble(cell);
                return -1;
            } catch (NumberFormatException e) {
                return 1;
            }
        }
        if (type.l == ValueTypes.DATE) {
            ParsedDate dt = typeParser.parse(cell, ParsedDate.class);
            return (dt == null) ? 1 : -1;
        }
        if (type.l == ValueTypes.BOOLEAN) {
            Boolean bool = typeParser.parse(cell, Boolean.class);
            return (bool == null) ? 1 : -1;
        }
        return 0;
    }

    public LinesContainer readLines(String data) {
        String[] lines = null;
        String lineEnding = null;
        double minVariance = Double.MAX_VALUE;
        for (String ending : lineEndings) {
            if (log.isDebugEnabled()) {
                log.debug("try ending [" + StringEscapeUtils.escapeJava(ending) + "]");
            }
            Pattern p = Pattern.compile(ending);
            String[] ls = p.split(data);
            double meanLength = getMeanLineLength(ls);
            double sd = Math.sqrt(getLineLengthVariance(ls, meanLength));
            String[] filtered = removeOutliers(ls, meanLength, sd);
            double newMeanLength = getMeanLineLength(filtered);
            double newVariance = getLineLengthVariance(filtered, newMeanLength);
            if (log.isDebugEnabled()) {
                log.debug("length=" + ls.length + ", var=" + newVariance);
            }
            if (ls.length > 1 && newVariance < minVariance) {
                minVariance = newVariance;
                lines = ls;
                lineEnding = ending;
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("selected ending [" + StringEscapeUtils.escapeJava(lineEnding) + "]");
        }
        // Test line ending for files with a single line
        if (lineEnding == null) {
            for (String ending : lineEndings) {
                if (data.replaceAll("[^(" + ending + ")]+", "").length() > 0) {
                    lineEnding = ending;
                    break;
                }
            }
        }
        return new LinesContainer(lines, lineEnding);
    }

    private double getMeanLineLength(String[] lines) {
        if (lines.length == 0) return 0;
        double sum = 0.0;
        for (String line : lines) {
            sum += line.length();
        }
        return sum / lines.length;
    }

    private double getLineLengthVariance(String[] lines, double mean) {
        if (lines.length == 0) return 0;
        double temp = 0.0;
        for (String line : lines) {
            temp += (mean - line.length()) * (mean - line.length());
        }
        return temp / lines.length;
    }

    private String[] removeOutliers(String[] lines, double mean, double sd) {
        List<String> filtered = new ArrayList<>();
        for (String line : lines) {
            // approximate by excluding lines with lengths greater than or equal to
            // 2 standard deviations from the mean
            // Chauvenet's criterion is a common method but requires a normal distribution function
            if (Math.sqrt((mean - line.length()) * (mean - line.length())) / sd < 2) {
                filtered.add(line);
            }
        }
        return filtered.toArray(new String[filtered.size()]);
    }

    private double getModeLineLength(String[] lines) {
        if (lines.length == 0) return 0;
        int modeCount = 0;
        int mode = 0;
        int currCount = 0;

        for (String line : lines) {
            // Reset the number of times we have seen the current value
            currCount = 0;

            // Iterate through the array counting the number of times we see the current candidate mode
            for (String ln : lines) {
                // If they match, increment the current count
                if (line.length() == ln.length()) {
                    currCount++;
                }
            }
            // We only save this candidate mode, if its count is greater than the current mode
            // we have stored in the "mode" variable
            if (currCount > modeCount) {
                modeCount = currCount;
                mode = line.length();
            }
        }
        return mode;
    }

    public String[] getHeader(String[][] rows, TypeInfo[] types, boolean hasHeader) {
        String[] header;
        if (hasHeader) {
            header = rows[0];
            for (int i = 0; i < header.length; i++) {
                if (header[i] != null) {
                    String columnName = header[i].trim();
                    if (!columnName.isEmpty()) {
                        header[i] = columnName;
                        continue;
                    }
                }
                header[i] = types[i].toString().toLowerCase() + "_" + (i + 1);
            }
        } else {
            header = makeHeaderNames(types);
        }
        return header;
    }

    public String[] getHeader(List<List<String>> rows, TypeInfo[] types, boolean hasHeader) {
        int n = rows.size();
        String[][] data = new String[n][];
        for (int i = 0; i < n; i++) {
            List<String> row = rows.get(i);
            data[i] = row.toArray(new String[row.size()]);
        }
        return getHeader(data, types, hasHeader);
    }

    public String[] makeHeaderNames(TypeInfo[] types) {
        String[] header = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            header[i] = types[i].toString().toLowerCase() + "_" + (i + 1);
        }
        return header;
    }

    public TypesContainer getTypes(String[][] rows, int sampleSize, int maxNumberColumns, boolean hasHeader) {
        TypeInfo[] types = new TypeInfo[maxNumberColumns];
        DataTypes[] sqlTypes = new DataTypes[maxNumberColumns];

        int start = hasHeader ? 1 : 0;
        for (int i = start; i < sampleSize; i++) {
            String[] sampleRow = rows[i];
            for (int j = 0; j < maxNumberColumns; j++) {
                TypeInfo type = deduceDataType(sampleRow[j]);
                if (types[j] == null) {
                    types[j] = type;
                    sqlTypes[j] = getSqlType(type.getType());
                } else {
                    if (types[j].equals(type)) {
                        if (typeHierarchy.indexOf(type.getType()) > typeHierarchy.indexOf(types[j].getType())) {
                            types[j] = type;
                            sqlTypes[j] = getSqlType(type.getType());
                        }
                    }
                }
            }
        }
        return new TypesContainer(types, sqlTypes);
    }

    public TypesContainer getTypes(List<List<String>> rows, int sampleSize, int maxNumberColumns, boolean hasHeader) {
        int n = Math.min(rows.size(), sampleSize);
        String[][] data = new String[n][];
        for (int i = 0; i < n; i++) {
            List<String> row = rows.get(i);
            data[i] = row.toArray(new String[row.size()]);
        }
        return getTypes(data, sampleSize, maxNumberColumns, hasHeader);
    }

    public DataTypes getSqlType(ValueTypes type) {
        switch (type) {
            case INTEGER:
                return DataTypes.INTEGER;
            case NUMERIC:
                return DataTypes.NUMERIC;
            case BOOLEAN:
                return DataTypes.BOOLEAN;
            case BIT:
                return DataTypes.BOOLEAN;
            case DATE:
                return DataTypes.TIMESTAMP;
            case TEXT:
                return DataTypes.TEXT;
            default:
                return DataTypes.NVARCHAR;
        }
    }

    public ParsedDate parseDate(String value) {
        return typeParser.parse(value, ParsedDate.class);
    }

    public boolean parseBoolean(String value) {
        return typeParser.parse(value, Boolean.class);
    }

    public boolean isDefaultName(String name) {
        if (hasValue(name)) {
            for (ValueTypes type : typeHierarchy) {
                if (name.startsWith(type.toString().toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }
}
