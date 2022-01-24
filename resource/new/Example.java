public class Example {

    public static void main() {
        String str = "Before";
        System.out.println(str);
        
        int a = 10;
        int b = 5;
        double q = 0;

        if(a > 0) {
            q = b / a;
        }
        put(str, "After");
    }
    
    public static void put(String oldStr, String newStr) {
        oldStr = newStr;
    }

}