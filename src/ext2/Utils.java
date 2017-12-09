package ext2;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public final class Utils {

    public static String epochTimeToDate(int time) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss a");
        return sdf.format(new Date(time * 1000L));
    }

    public static ArrayList<String> splitPath(String path) {
        String[] split = path.split("/");
        ArrayList<String> directories = new ArrayList<>(split.length);
        for (String dir : split)
            if (dir.length() > 0)
                directories.add(dir);
        return directories;
    }

    public static boolean containsIllegals(String text) {
        String[] arr = text.split("[/~#@*+%{}<>\\[\\]|\"_^]", 2);
        return arr.length > 1;
    }

    public static ArrayList<Integer> intsToList(int array[]) {
        ArrayList<Integer> list = new ArrayList<>();
        for (int i : array) list.add(i);
        return list;
    }
}
