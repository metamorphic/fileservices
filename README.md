# fileservices

Extract structure from flat files

Example usage:

    FileService fs = new FileServiceImpl();

    String data = readFileAsString(file);

    // check that we're not handling a JSON file instead
    Pattern startJsonFilePattern = Pattern.compile("^\\s*[\\[\\{]", Pattern.MULTILINE);
    Matcher matcher = startJsonFilePattern.matcher(data);

    if (matcher.find()) {
        if (log.isDebugEnabled()) {
            log.debug("Reading JSON");
        }
        return readJsonSample(name, data);
    }
    if (log.isDebugEnabled()) {
        log.debug("Reading Delimited");
    }
    
    // strip blank lines at the start of the file
    data = data.replaceAll("^\\s+", "");

    // infer the line ending
    LinesContainer lc = fs.readLines(data);
    String[] lines = lc.lines;
    String lineEnding = lc.lineEnding;

    if (log.isDebugEnabled()) {
        log.debug("\nline ending [" + StringEscapeUtils.escapeJava(lineEnding) + "]");
    }

    // infer file parameters
    FileParameters fileParameters = fs.sniff(data, lineEnding);

    if (fileParameters == null) {
        return new DatasetInfo("Could not determine file parameters");
    }
    

Returns a `FileParameters` object containing:
* Text Qualifier (quoting schema)
* Is Double Quoting used?
* Column Delimiter
* Has Header?
* Line Terminator
 
 
## Building the project

To build the project:

    ./gradlew clean build
    
Assumes use of Artifactory, so Artifactory host and user variables must be set in gradle config.


## Dependencies

* https://github.com/metamorphic/metamorphic-commons
* Jackson
* commons-lang
* commons-logging
