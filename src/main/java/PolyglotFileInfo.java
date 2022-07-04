public class PolyglotFileInfo {

    private boolean isHost;
    private String uri;
    private String language;

    public PolyglotFileInfo(boolean isHost, String uri, String language){
        this.isHost = isHost;
        this.uri = uri;
        this.language = language;
    }

    public PolyglotFileInfo(String uri){
        String[] splitURI = uri.split("[.]", 0);
        String extensionFile = splitURI[splitURI.length-1];
        this.isHost = splitURI.length >= 2 && splitURI[splitURI.length-2].length() >= 4 && splitURI[splitURI.length-2].substring(splitURI[splitURI.length-2].length()-4).equals("host");
        this.uri = uri;
        this.language = getLanguageFromExtension(extensionFile);
    }

    private String getLanguageFromExtension(String extension){
        switch (extension){
            case "js":
                return "javascript";
            case "py":
                return "python";
            default:
                return "none";
        }
    }

    public boolean isHost() {
        return isHost;
    }

    public void setHost(boolean host) {
        isHost = host;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
