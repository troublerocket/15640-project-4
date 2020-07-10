import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/* logHandler class for processing the log file */
public class logHandler{

    /**
     * serialize the message to bytes
     * 
     * @param obj message instance
     * @return serialized message bytes
     */
    public static byte[] serialize(Object obj){
        try(ByteArrayOutputStream b = new ByteArrayOutputStream()){
            try(ObjectOutputStream o = new ObjectOutputStream(b)){
                o.writeObject(obj);
            }catch(IOException e){
            }
            return b.toByteArray();
        } catch (IOException e) {
        }
        return null;
    }

    /**
     * deserialize bytes to message
     * 
     * @param bytes message in bytes
     * @return message instance
     */
    public static NewMessage deserialize(byte[] bytes){
        try(ByteArrayInputStream b = new ByteArrayInputStream(bytes)){
            try(ObjectInputStream o = new ObjectInputStream(b)){
                return (NewMessage)o.readObject();
            }catch(ClassNotFoundException e){
            }
        }catch(IOException e){
        }
        return null;
    }

    /**
     * write log and flush the data to disk
     * 
     * @param PL ProjectLib instance
     * @param dest  filename on disk
     * @param content contents to write in log
     */
    public static void writeLog(ProjectLib PL,String dest, String content){
        try (BufferedWriter b = 
                            new BufferedWriter(new FileWriter(dest, true))){
            b.write(String.format("%s\n", content));
            b.flush();
            b.close();
        }catch (IOException e){
            e.printStackTrace();
        }
        PL.fsync();
    }


    /**
     * read log and format the contents
     * 
     * @param dest the filename on disk
     * @return cotents in log file
     */
    public static List<String> readLog(String dest){
        List<String> strings = new ArrayList<String>();
        Scanner fileScanner = null;
        File file=new File(dest);
        if (file.exists()){
            try{
                fileScanner = new Scanner(file);
                while (fileScanner.hasNext()){
                    strings.add(fileScanner.next());
                }
                fileScanner.close();
            }catch (FileNotFoundException e){
                e.printStackTrace();
            }
        }
        return strings;
    }

}