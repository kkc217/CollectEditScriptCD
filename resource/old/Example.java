public class Example {

    public static void main() {
        String str = "Before";
        put(str, "After");

        int a = 10;
        int b = 5;
        double q = 0;

        if(a > 0 && b > 0) {
            q = a / b;
        }

    }
    
    public static void put(String oldStr, String newStr) {
        oldStr = newStr;
    }

}