import java.util.ArrayList;

public class PolyglotLanguageServerProperties {

    public ArrayList<LanguageServerInfo> ls;

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

        String language;
        String ip;
        int port;
        ArrayList<String> command;
        String hoverRegex;
        int hoverRegexGroup;

    }

}
