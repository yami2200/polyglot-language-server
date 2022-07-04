public class PolyglotLanguagesLib {

    public static int getUnderlineLengthNotFoundFile(String language){
        switch (language){
            case "javascript":
            case "js":
                return 2;
            case "python":
                return 7;
        }
        return 0;
    }

}
