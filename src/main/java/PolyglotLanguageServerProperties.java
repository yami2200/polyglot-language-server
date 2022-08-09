import java.util.ArrayList;

public class PolyglotLanguageServerProperties {

    public ArrayList<LanguageServerInfo> ls; // List of language servers properties

    public PolyglotLanguageServerProperties() { ls = new ArrayList<>(); }

    public PolyglotLanguageServerProperties(ArrayList<LanguageServerInfo> ls) {
        this.ls = ls;
    }

    protected class LanguageServerInfo{

        public LanguageServerInfo(String language, String ip, int port, ArrayList<String> command, String hoverRegex, int hoverRegexGroup) {
            this.language = language;
            this.ip = ip;
            this.port = port;
            this.command = command;
            this.hoverRegex = hoverRegex;
            this.hoverRegexGroup = hoverRegexGroup;
        }

        String language; // Programming language of language server
        String ip; // IP of language server
        int port; // port of language server
        ArrayList<String> command; // list of command/args to launch the language server
        String hoverRegex; // Regex to apply to Language Server hover text to isolate the type of variable
        int hoverRegexGroup; // Group to catch from Language Server hover text to isolate the type of variable

    }

}
