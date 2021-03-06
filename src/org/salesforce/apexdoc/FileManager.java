package org.salesforce.apexdoc;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

enum OutputType{HTML, MARKDOWN}

public class FileManager {
    FileOutputStream fos;
    DataOutputStream dos;
    String path;
    public String header;
    public String APEX_DOC_PATH = "";
    public StringBuffer infoMessages;
    public OutputType outputFormat = OutputType.HTML;
    public void setoutputFormat(OutputType whatFormat){
        outputFormat = whatFormat;
    }

    public FileManager() {
        infoMessages = new StringBuffer();

        // The default output type is still HTML
        outputFormat = OutputType.HTML;
    }

    private static String escapeHTML(String s) {
        StringBuilder out = new StringBuilder(Math.max(16, s.length()));
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c > 127 || c == '"' || c == '<' || c == '>' || c == '&') {
                out.append("&#");
                out.append((int) c);
                out.append(';');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public FileManager(String path) {
        infoMessages = new StringBuffer();

        if (path == null || path.trim().length() == 0)
            this.path = ".";
        else
            this.path = path;
    }

    private boolean writeFiles(TreeMap<String, String> mapFNameToContent, IProgressMonitor monitor) {
        String fileSuffix = ".html";
        if (outputFormat == OutputType.MARKDOWN)
            fileSuffix = ".md";

        try {
            if (path.endsWith("/") || path.endsWith("\\")) {
                path += Constants.ROOT_DIRECTORY; // + "/" + fileName + ".html";
            } else {
                path += "/" + Constants.ROOT_DIRECTORY; // + "/" + fileName + ".html";
            }

            (new File(path)).mkdirs();

            for (String fileName : mapFNameToContent.keySet()) {
                String contents = mapFNameToContent.get(fileName);
                fileName = path + "/" + fileName + fileSuffix;
                File file = new File(fileName);
                fos = new FileOutputStream(file);
                dos = new DataOutputStream(fos);
                dos.write(contents.getBytes());
                dos.close();
                fos.close();
                infoMessages.append(fileName + " Processed...\n");
                System.out.println(fileName + " Processed...");
                if (monitor != null)
                    monitor.worked(1);
            }
            copy(path);
            return true;
        } catch (Exception e) {

            e.printStackTrace();
        }

        return false;
    }

    private String strLinkfromModel(ApexModel model, String strClassName, String hostedSourceURL) {
        return "<a target='_blank' class='hostedSourceLink' href='" + hostedSourceURL + strClassName + ".cls#L"
                + model.getInameLine() + "'>";
    }

    private String strHTMLScopingPanel() {
        String str = "<tr><td colspan='2' style='text-align: center;' >";
        str += "Show: ";

        for (int i = 0; i < ApexDoc.rgstrScope.length; i++) {
            str += "<input type='checkbox' checked='checked' id='cbx" + ApexDoc.rgstrScope[i] +
                    "' onclick='ToggleScope(\"" + ApexDoc.rgstrScope[i] + "\", this.checked );'>" +
                    ApexDoc.rgstrScope[i] + "</input>&nbsp;&nbsp;";
        }
        str += "</td></tr>";
        return str;
    }

    /********************************************************************************************
     * @description main routine that creates an HTML file for each class specified
     * @param mapGroupNameToClassGroup
     * @param cModels
     * @param projectDetail
     * @param homeContents
     * @param hostedSourceURL
     * @param monitor
     */
    private void makeFile(TreeMap<String, ClassGroup> mapGroupNameToClassGroup, ArrayList<ClassModel> cModels,
                          String projectDetail, String homeContents, String hostedSourceURL, IProgressMonitor monitor) {
        String links = "<table width='100%'>";
        links += strHTMLScopingPanel();
        links += "<tr style='vertical-align:top;' >";
        links += getPageLinks(mapGroupNameToClassGroup, cModels);

        if (homeContents != null && homeContents.trim().length() > 0) {
            homeContents = links + "<td class='contentTD'>" + "<h2 class='section-title'>Home</h2>" + homeContents + "</td>";
            homeContents = Constants.getHeader(projectDetail) + homeContents + Constants.FOOTER;
        } else {
            homeContents = Constants.DEFAULT_HOME_CONTENTS;
            homeContents = links + "<td class='contentTD'>" + "<h2 class='section-title'>Home</h2>" + homeContents + "</td>";
            homeContents = Constants.getHeader(projectDetail) + homeContents + Constants.FOOTER;
        }

        String fileName = "";
        TreeMap<String, String> mapFNameToContent = new TreeMap<String, String>();
        mapFNameToContent.put("index", homeContents);

        // create our Class Group content files
        createClassGroupContent(mapFNameToContent, links, projectDetail, mapGroupNameToClassGroup, cModels, monitor);

        if (outputFormat == OutputType.HTML)
            createHTMLFiles(cModels, projectDetail, hostedSourceURL, monitor, links, mapFNameToContent);
        else
            createMarkdownFiles(cModels, projectDetail, hostedSourceURL, monitor, links, mapFNameToContent);
    }

    private void createHTMLFiles(ArrayList<ClassModel> cModels, String projectDetail, String hostedSourceURL, IProgressMonitor monitor, String links, TreeMap<String, String> mapFNameToContent) {
        String fileName;
        for (ClassModel cModel : cModels) {
            String contents = links;
            if (cModel.getNameLine() != null && cModel.getNameLine().length() > 0) {
                fileName = cModel.getClassName();
                contents += "<td class='contentTD'>";

                contents += htmlForClassModel(cModel, hostedSourceURL);

                // deal with any nested classes
                for (ClassModel cmChild : cModel.getChildClassesSorted()) {
                    contents += "<p/>";
                    contents += htmlForClassModel(cmChild, hostedSourceURL);
                }

            } else {
                continue;
            }
            contents += "</div>";

            contents = Constants.getHeader(projectDetail) + contents + Constants.FOOTER;
            mapFNameToContent.put(fileName, contents);
            if (monitor != null)
                monitor.worked(1);
        }
        writeFiles(mapFNameToContent, monitor);
    }

    private void createMarkdownFiles(ArrayList<ClassModel> cModels, String projectDetail, String hostedSourceURL, IProgressMonitor monitor, String links, TreeMap<String, String> mapFNameToContent) {
        String fileName;
        for (ClassModel cModel : cModels) {
            String contents = "";
            if (cModel.getNameLine() != null && cModel.getNameLine().length() > 0) {
                fileName = cModel.getClassName();

                contents += markdownForClassModel(cModel, hostedSourceURL);

                // deal with any nested classes
                for (ClassModel cmChild : cModel.getChildClassesSorted()) {
                    contents += "<p/>";
                    contents += markdownForClassModel(cmChild, hostedSourceURL);
                }

            } else {
                continue;
            }
            //contents += "</div>";

            //contents = Constants.getHeader(projectDetail) + contents + Constants.FOOTER;
            mapFNameToContent.put(fileName, contents);
            if (monitor != null)
                monitor.worked(1);
        }
        writeFiles(mapFNameToContent, monitor);
    }


    /*********************************************************************************************
     * @description creates the Markdown content for the provided class, including its
     *              property and methods
     * @param cModel
     * @param hostedSourceURL
     * @return markdown string
     */
    private String markdownForClassModel(ClassModel cModel, String hostedSourceURL){
        String mdBreak = "  " + "\n";
        String mdTableOpen = "| Name | Description |" + mdBreak + "|:-|:-|" + mdBreak;
        String mdHR = "\n\n---\n";
        String mdPreOpen = "``` Java\n"; // Preformatted code
        String mdPreClose = "\n```\n";
        String mdTOC = "\n[[_TOC_]]\n";
        String contents = "";

        // Class Header
        contents += "# " + cModel.getClassName() + mdBreak;

        if(cModel.getDeprecated() != ""){
            contents += "<font color='Red'>" + "Deprecated: " + escapeHTML(cModel.getDeprecated()) + "</font>";
            contents += mdBreak + mdBreak;
        }

        if (cModel.getDescription() != "")
            contents += "" + cModel.getDescription() + mdBreak + mdBreak;

        if (cModel.getAuthor() != "")
            contents += "Author: " + cModel.getAuthor() + mdBreak;

        if (cModel.getDate() != "")
            contents += "Date: " + cModel.getDate() + mdBreak;

        contents += mdBreak;

        // not quite ready for this, yet
        //contents += mdTOC;

        // full name line as link
        //contents += strLinkfromModel(cModel, cModel.getTopmostClassName(), hostedSourceURL) +
        //        escapeHTML(cModel.getNameLine()) + "</a>" + mdBreak;
        contents += mdPreOpen + cModel.getNameLine() + mdPreClose;

        if (cModel.getProperties().size() > 0) {
            // start Properties
            contents += "## Properties" + mdBreak;
            contents += mdTableOpen;

            for (PropertyModel prop : cModel.getPropertiesSorted()) {
                contents += "" + prop.getScope() + "" +
                        prop.getPropertyName() + "";
                contents += "" +
                        strLinkfromModel(prop, cModel.getTopmostClassName(), hostedSourceURL) +
                        escapeHTML(prop.getNameLine()) + "</a>";
                contents += " | " + escapeHTML(prop.getDescription()) + "";
            }
        }// end Properties

        if (cModel.getMethods().size() > 0) {
            // start Methods
            contents += "## Methods";
            contents += mdBreak;

            // method Table of Contents (TOC)
            contents += mdTableOpen;
            for (MethodModel method : cModel.getMethodsSorted()) {
                contents += "" + method.getScope() + " ";
                //contents += "<a href='#" + method.getMethodName() + "'>" + method.getMethodName() + "</a>";
                contents += method.getMethodName();
                contents += " | ";
                if (method.getDescription() != "")
                    contents += "" + method.getDescription() + "";
                contents += mdBreak;
            }

            // full method display
            for (MethodModel method : cModel.getMethodsSorted()) {
                contents += mdHR;
                contents += "## " + method.getMethodName() + mdBreak;

                if(method.getDeprecated() != ""){
                    contents += "<font color='Red'>" + "Deprecated: " + escapeHTML(method.getDeprecated()) + "</font>";
                    contents += mdBreak;
                }

                contents += mdBreak;

                if (method.getDescription() != "")
                    contents += "" + escapeHTML(method.getDescription()) + mdBreak;

                //contents += strLinkfromModel(method, cModel.getTopmostClassName(), hostedSourceURL) +
                //        escapeHTML(method.getNameLine()) + "</a>";
                contents += mdPreOpen + cModel.getNameLine() + mdPreClose;

                contents += mdBreak;

                if (method.getParams().size() > 0) {
                    contents += "### Parameters" + mdBreak;
                    contents += mdTableOpen;
                    for (String param : method.getParams()) {
                        param = escapeHTML(param);
                        if (param != null && param.trim().length() > 0) {
                            Pattern p = Pattern.compile("\\s");
                            Matcher m = p.matcher(param);

                            String paramName;
                            String paramDescription;
                            if (m.find()) {
                                int ich = m.start();
                                paramName = param.substring(0, ich);
                                paramDescription = param.substring(ich + 1);
                            } else {
                                paramName = param;
                                paramDescription = null;
                            }
                            contents += "" + paramName + " | ";

                            if (paramDescription != null)
                                contents += "" + paramDescription;

                            contents += mdBreak;
                        }
                    }
                    // end Parameters
                }

                if (method.getReturns() != "") {
                    contents += "### Return Value" + mdBreak;
                    contents += "" + escapeHTML(method.getReturns()) + mdBreak;
                }

                if (method.getExample() != "") {
                    contents += "### Example" + mdBreak;
                    contents += "<code class='methodExample'>" + escapeHTML(method.getExample()) + "</code>";
                }

                if (method.getAuthor() != "") {
                    contents += "## Author" + mdBreak;
                    contents += "" + escapeHTML(method.getAuthor()) + mdBreak;
                }

                if (method.getExceptionList().size() > 0) {
                    contents += "### Exceptions" + mdBreak;
                    for(String except : method.getExceptionList()){
                        contents += "<div class='methodReturns'>" + escapeHTML(except) + mdBreak;
                    }
                }

                if (method.getDate() != "") {
                    contents += "### Date" + mdBreak;
                    contents += "" + escapeHTML(method.getDate()) + mdBreak;
                }

                // end current method
                //contents += mdBreak;
            }
            // end all methods
            contents += mdBreak;
        }

        return contents;
    }

    /*********************************************************************************************
     * @description creates the HTML for the provided class, including its
     *              property and methods
     * @param cModel
     * @param hostedSourceURL
     * @return html string
     */
    private String htmlForClassModel(ClassModel cModel, String hostedSourceURL) {
        String contents = "";
        contents += "<h2 class='section-title'>" +
                strLinkfromModel(cModel, cModel.getTopmostClassName(), hostedSourceURL) +
                cModel.getClassName() + "</a>" +
                "</h2>";

        contents += "<div class='classSignature'>" +
                strLinkfromModel(cModel, cModel.getTopmostClassName(), hostedSourceURL) +
                escapeHTML(cModel.getNameLine()) + "</a></div>";

        contents += "<div class='classDetails'>";

        if(cModel.getDeprecated() != ""){
            contents +="<span class='warning'>Deprecated</span>: " + escapeHTML(cModel.getDeprecated()) + "<br><br/>";
        }

        if (cModel.getDescription() != "")
            contents += "" + escapeHTML(cModel.getDescription()) + "<br><br>";

        if (cModel.getAuthor() != "")
            contents += "Author: " + escapeHTML(cModel.getAuthor()) + "<br>";

        if (cModel.getDate() != "")
            contents += "Date: " + escapeHTML(cModel.getDate());
        contents += "</div><p/>";
        contents += "</div><p/>";

        if (cModel.getProperties().size() > 0) {
            // start Properties
            contents +=
                    "<h2 class='subsection-title'>Properties</h2>" +
                            "<div class='subsection-container'> " +
                            "<table class='properties' > ";

            for (PropertyModel prop : cModel.getPropertiesSorted()) {
                contents += "<tr class='propertyscope" + prop.getScope() + "'><td class='clsPropertyName'>" +
                        prop.getPropertyName() + "</td>";
                contents += "<td><div class='clsPropertyDeclaration'>" +
                        strLinkfromModel(prop, cModel.getTopmostClassName(), hostedSourceURL) +
                        escapeHTML(prop.getNameLine()) + "</a></div>";
                contents += "<div class='clsPropertyDescription'>" + escapeHTML(prop.getDescription()) + "</div></tr>";
            }
            // end Properties
            contents += "</table></div><p/>";
        }

        if (cModel.getMethods().size() > 0) {
            // start Methods
            contents +=
                    "<h2 class='subsection-title'>Methods</h2>" +
                            "<div class='subsection-container'> ";

            // method Table of Contents (TOC)
            contents += "<ul class='methodTOC'>";
            for (MethodModel method : cModel.getMethodsSorted()) {
                contents += "<li class='methodscope" + method.getScope() + "' >";
                contents += "<a class='methodTOCEntry' href='#" + method.getMethodName() + "'>"
                        + method.getMethodName() + "</a>";
                if (method.getDescription() != "")
                    contents += "<div class='methodTOCDescription'>" + method.getDescription() + "</div>";
                contents += "</li>";
            }
            contents += "</ul>";

            // full method display
            for (MethodModel method : cModel.getMethodsSorted()) {
                contents += "<div class='methodscope" + method.getScope() + "' >";
                contents += "<h2 class='methodHeader'><a id='" + method.getMethodName() + "'/>"
                        + method.getMethodName() + "</h2>" +
                        "<div class='methodSignature'>" +
                        strLinkfromModel(method, cModel.getTopmostClassName(), hostedSourceURL) +
                        escapeHTML(method.getNameLine()) + "</a></div>";

                if(method.getDeprecated() != ""){
                    contents +="<div class='methodSubTitle warning'>Deprecated</div>";
                    contents += "<div class='methodReturns'>" + escapeHTML(method.getDeprecated()) + "</div>";
                }

                if (method.getDescription() != "")
                    contents += "<div class='methodDescription'>" + escapeHTML(method.getDescription()) + "</div>";

                if (method.getParams().size() > 0) {
                    contents += "<div class='methodSubTitle'>Parameters</div>";
                    for (String param : method.getParams()) {
                        param = escapeHTML(param);
                        if (param != null && param.trim().length() > 0) {
                            Pattern p = Pattern.compile("\\s");
                            Matcher m = p.matcher(param);

                            String paramName;
                            String paramDescription;
                            if (m.find()) {
                                int ich = m.start();
                                paramName = param.substring(0, ich);
                                paramDescription = param.substring(ich + 1);
                            } else {
                                paramName = param;
                                paramDescription = null;
                            }
                            contents += "<div class='paramName'>" + paramName + "</div>";

                            if (paramDescription != null)
                                contents += "<div class='paramDescription'>" + paramDescription + "</div>";
                        }
                    }
                    // end Parameters
                }

                if (method.getReturns() != "") {
                    contents += "<div class='methodSubTitle'>Return Value</div>";
                    contents += "<div class='methodReturns'>" + escapeHTML(method.getReturns()) + "</div>";
                }

                if (method.getExample() != "") {
                    contents += "<div class='methodSubTitle'>Example</div>";
                    contents += "<code class='methodExample'>" + escapeHTML(method.getExample()) + "</code>";
                }

                if (method.getAuthor() != "") {
                    contents += "<div class='methodSubTitle'>Author</div>";
                    contents += "<div class='methodReturns'>" + escapeHTML(method.getAuthor()) + "</div>";
                }

                if (method.getExceptionList().size() > 0) {
                    contents += "<div class='methodSubTitle'>Exceptions</div>";
                    for(String except : method.getExceptionList()){
                        contents += "<div class='methodReturns'>" + escapeHTML(except) + "</div>";
                    }
                }

                if (method.getDate() != "") {
                    contents += "<div class='methodSubTitle'>Date</div>";
                    contents += "<div class='methodReturns'>" + escapeHTML(method.getDate()) + "</div>";
                }

                // end current method
                contents += "</div>";
            }
            // end all methods
            contents += "</div>";
        }

        return contents;
    }

    // create our Class Group content files
    private void createClassGroupContent(TreeMap<String, String> mapFNameToContent, String links, String projectDetail,
                                         TreeMap<String, ClassGroup> mapGroupNameToClassGroup,
                                         ArrayList<ClassModel> cModels, IProgressMonitor monitor) {

        for (String strGroup : mapGroupNameToClassGroup.keySet()) {
            ClassGroup cg = mapGroupNameToClassGroup.get(strGroup);
            if (cg.getContentSource() != null) {
                String cgContent = parseHTMLFile(cg.getContentSource());
                if (cgContent != "") {
                    String strHtml = Constants.getHeader(projectDetail) + links + "<td class='contentTD'>" +
                            "<h2 class='section-title'>" +
                            escapeHTML(cg.getName()) + "</h2>" + cgContent + "</td>";
                    strHtml += Constants.FOOTER;
                    mapFNameToContent.put(cg.getContentFilename(), strHtml);
                    if (monitor != null)
                        monitor.worked(1);
                }
            }
        }
    }

    /**********************************************************************************************************
     * @description generate the HTML string for the Class Menu to display on
     *              each page.
     * @param mapGroupNameToClassGroup
     *            map that holds all the Class names, and their respective Class
     *            Group.
     * @param cModels
     *            list of ClassModels
     * @return String of HTML
     */
    private String getPageLinks(TreeMap<String, ClassGroup> mapGroupNameToClassGroup, ArrayList<ClassModel> cModels) {
        boolean createMiscellaneousGroup = false;

        // this is the only place we need the list of class models sorted by name.
        TreeMap<String, ClassModel> tm = new TreeMap<String, ClassModel>();
        for (ClassModel cm : cModels) {
            tm.put(cm.getClassName().toLowerCase(), cm);
            if (!createMiscellaneousGroup && cm.getClassGroup() == null)
                createMiscellaneousGroup = true;
        }
        cModels = new ArrayList<ClassModel>(tm.values());

        String links = "<td width='20%' vertical-align='top' >";
        links += "<div class='sidebar'><div class='navbar'><nav role='navigation'><ul id='mynavbar'>";
        links += "<li id='idMenuindex'><a href='.' onclick=\"gotomenu('index.html', event);return false;\" class='nav-item'>Home</a></li>";

        // add a bucket ClassGroup for all Classes without a ClassGroup specified
        if (createMiscellaneousGroup)
            mapGroupNameToClassGroup.put("Miscellaneous", new ClassGroup("Miscellaneous", null));

        // create a sorted list of ClassGroups

        for (String strGroup : mapGroupNameToClassGroup.keySet()) {
            ClassGroup cg = mapGroupNameToClassGroup.get(strGroup);
            String strGoTo = "onclick=\"gotomenu(document.location.href, event);return false;\"";
            if (cg.getContentFilename() != null)
                strGoTo = "onclick=\"gotomenu('" + cg.getContentFilename() + ".html" + "', event);return false;\"";
            links += "<li class='header' id='idMenu" + cg.getContentFilename() +
                    "'><a class='nav-item nav-section-title' href='.' " +
                    strGoTo + " class='nav-item'>" + strGroup + "<span class='caret'></span></a></li>";
            links += "<ul>";

            // even though this algorithm is O(n^2), it was timed at just 12
            // milliseconds, so not an issue!
            for (ClassModel cModel : cModels) {
                if (strGroup.equals(cModel.getClassGroup())
                        || (cModel.getClassGroup() == null && strGroup == "Miscellaneous")) {
                    if (cModel.getNameLine() != null && cModel.getNameLine().trim().length() > 0) {
                        String fileName = cModel.getClassName();
                        links += "<li class='subitem classscope" + cModel.getScope() + "' id='idMenu" + fileName +
                                "'><a href='.' onclick=\"gotomenu('" + fileName + ".html', event);return false;\" class='nav-item sub-nav-item scope" +
                                cModel.getScope() + "'>" +
                                fileName + "</a></li>";
                    }
                }
            }

            links += "</ul>";
        }

        links += "</ul></nav></div></div></div>";

        links += "</td>";
        return links;
    }

    private void docopy(String source, String target) throws Exception {

        InputStream is = this.getClass().getResourceAsStream(source);
        // InputStreamReader isr = new InputStreamReader(is);
        // BufferedReader reader = new BufferedReader(isr);
        FileOutputStream to = new FileOutputStream(target + "/" + source);

        byte[] buffer = new byte[4096];
        int bytesRead;

        while ((bytesRead = is.read(buffer)) != -1) {
            to.write(buffer, 0, bytesRead); // write
        }

        to.flush();
        to.close();
        is.close();
    }

    private void copy(String toFileName) throws IOException, Exception {
        docopy("apex_doc_logo.png", toFileName);
        docopy("ApexDoc.css", toFileName);
        docopy("ApexDoc.js", toFileName);
        docopy("CollapsibleList.js", toFileName);
        docopy("jquery-1.11.1.js", toFileName);
        docopy("toggle_block_btm.gif", toFileName);
        docopy("toggle_block_stretch.gif", toFileName);

    }

    public ArrayList<File> getFiles(String path) {
        File folder = new File(path);
        ArrayList<File> listOfFilesToCopy = new ArrayList<File>();
        if (folder != null) {
            File[] listOfFiles = folder.listFiles();
            if (listOfFiles != null && listOfFiles.length > 0) {
                for (int i = 0; i < listOfFiles.length; i++) {
                    if (listOfFiles[i].isFile()) {
                        listOfFilesToCopy.add(listOfFiles[i]);
                    }
                }
            } else {
                System.out.println("WARNING: No files found in directory: " + path);
            }
        }
        return listOfFilesToCopy;
    }

    public void createDoc(TreeMap<String, ClassGroup> mapGroupNameToClassGroup, ArrayList<ClassModel> cModels,
                          String projectDetail, String homeContents, String hostedSourceURL, IProgressMonitor monitor) {
        makeFile(mapGroupNameToClassGroup, cModels, projectDetail, homeContents, hostedSourceURL, monitor);
    }

    private String parseFile(String filePath) {
        try {
            if (filePath != null && filePath.trim().length() > 0) {
                FileInputStream fstream = new FileInputStream(filePath);
                // Get the object of DataInputStream
                DataInputStream in = new DataInputStream(fstream);
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                String contents = "";
                String strLine;

                while ((strLine = br.readLine()) != null) {
                    // Print the content on the console
                    strLine = strLine.trim();
                    if (strLine != null && strLine.length() > 0) {
                        contents += strLine;
                    }
                }
                // System.out.println("Contents = " + contents);
                br.close();
                return contents;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    public String parseHTMLFile(String filePath) {

        String contents = (parseFile(filePath)).trim();
        if (contents != null && contents.length() > 0) {
            int startIndex = contents.indexOf("<body>");
            int endIndex = contents.indexOf("</body>");
            if (startIndex != -1) {
                if (contents.indexOf("</body>") != -1) {
                    contents = contents.substring(startIndex, endIndex);
                    return contents;
                }
            }
        }
        return "";
    }

}
