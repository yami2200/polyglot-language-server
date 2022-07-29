import java.util.ArrayList;

public class PolyglotLanguageServerProperties {

    public ArrayList<LanguageServerInfo> ls;

    public PolyglotLanguageServerProperties() { ls = new ArrayList<>(); }

    public PolyglotLanguageServerProperties(ArrayList<LanguageServerInfo> ls) {
        this.ls = ls;
    }

    protected class LanguageServerInfo{

        public LanguageServerInfo(String language, String ip, int port, ArrayList<String> command) {
            this.language = language;
            this.ip = ip;
            this.port = port;
            this.command = command;
        }

        String language;
        String ip;
        int port;
        ArrayList<String> command;

    }

}
