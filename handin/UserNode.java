import java.io.*;
import java.util.*;

/* UserNode class */
public class UserNode implements ProjectLib.MessageHandling{
    // the id of the usernode
    public static String myId;
    // the ProjectLib instance
    private static ProjectLib PL;
    // the name of the log file
    private static String log_name = null;

    // images to lock before the decision is known
    private static Map<String,Set<String>> lock_list = new HashMap<>();
    // images to remove after the collage is committed
    private static Set<String> remove_list = new HashSet<>();

    /**
     * UserNode constructor
     * 
     * @param id id of the usernode
     */
    public UserNode( String id ) {
        myId = id;
    }

    /**
     * receive and handle the message from server
     * 
     * @param msg message from server
     * @return true if the message is delivered and processed
     */
    public boolean deliverMessage( ProjectLib.Message msg ) {

        NewMessage message = logHandler.deserialize(msg.body);

        // the server asks for vote        
        if(message.type == 1) {
            askVote(message);
        }

        // the server distributes the dicision
        else if(message.type == 3){
            acceptDecision(message);
        }
        return true;
    }

    // 

    /**
     * check the vote of the usernode
     * 
     * @param message message from server
     * @return true if the usernode approves the collage, false otherwise
     */
    public boolean checkVote(NewMessage message){
        String collage = message.filename;
        String usernode = message.addr;
        
        // wrong usernode
        if(usernode.equals(myId) == false){
            return false;
        }
        // ignore duplicated vote request from the same collage
        if(lock_list.containsKey(collage)){
            return false;
        }

        byte[] contents = message.contents;
        LinkedList<String> imgs = message.imgs;

        // ask the user about the collage
        boolean vote = PL.askUser(contents, 
                                  imgs.toArray(new String[imgs.size()]));
        // the user is not happy with the collage
        if(vote == false){
            return false;
        }

        // the user is happy with the collage
        for(String img:imgs){
            if(remove_list.contains(img)){
                // image in the collage already deleted before
                return false;
            }
            for(String filename:lock_list.keySet()){
                // image in the collage already locked before
                if(lock_list.get(filename).contains(img)){
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * examine the candidate collage
     * 
     * @param message the message from server
     */
    public void askVote(NewMessage message){
        String collage = message.filename;
        LinkedList<String> imgs = message.imgs;

        // preparation for writing log
        StringBuffer log_content = new StringBuffer();
        log_content.append(collage);
        for(String img:imgs){
            log_content.append(";" + img);
        }

        
        if (checkVote(message) == false){// vote NO
            // write log for the vote step
            logHandler.writeLog(PL, log_name,
                                "Vote;false;" + log_content.toString());

            // send vote to the server
            sendMessage(2, collage, false);
        }
        
        else{// vote YES
            // write log for the vote step
            logHandler.writeLog(PL,log_name,
                                "Vote;true;" + log_content.toString());
            // lock the images included
            Set<String> locked_imgs = new HashSet<String>();
            for(String img:imgs){
                locked_imgs.add(img);
            }
            lock_list.put(collage, locked_imgs);

            // send vote to the server
            sendMessage(2, collage, true);
        }
    }

    /**
     * process after a decision from server is received
     * 
     * @param message the message from server
     */
    public void acceptDecision(NewMessage message){
        String collage = message.filename;
        boolean vote = message.vote;

        // no image need to lock for this collage
        if(lock_list.containsKey(collage) == false) {
            // send ack
            sendMessage(4, collage, vote);
            return;
        }
        // write log for decision step
        logHandler.writeLog(PL, log_name,
                            "Decision;" + vote +";"+collage);

        if(vote == true){// commit the collage 
            // remove included images from working directory
            for(String img:lock_list.get(collage)) {
                File file = new File(img);
                remove_list.add(img);
                file.delete();
            }
        }
        else{// no commit and abort the collage
        }

        // unlock included images
        lock_list.remove(collage);
        // send ack
        sendMessage(4, collage, vote);
    }

    /**
     * send message to server
     * 
     * @param type message type
     * @param filename filename of the collage
     * @param result the vote/ack of the usernode
     */
    public void sendMessage(int type, String filename, boolean result){

        NewMessage message = new NewMessage(filename, myId, result, type);
        byte[] body = logHandler.serialize(message);

        ProjectLib.Message msg = new ProjectLib.Message("Server", body);
        PL.sendMessage(msg);
    }

    /**
     * recover from failure based on the log file
     */
    public static void recoverFailure(List<String> logs){

        if(logs == null ){// no log and no collage commit yet
            return;
        }

        for(String line:logs){
            String[] content = line.split(";");
            // the current step to recover
            String step = content[0];
            // the status of the step
             String status = content[1];
            // the filename of the collage
            String collage = content[2];

            // failure after vote YES
            if (step.equals("Vote") && status.equals("true")){
                // relock the included images
                Set<String> lockImage = new HashSet<String>();
                lock_list.put(collage, lockImage);
                for(int i = 3; i < content.length; i++){
                    lockImage.add(content[i]);
                }
            }
            // failure after the decision received
            else if(step.equals("Decision")){
                // if the collage is aborted
                if(status.equals("false") && lock_list.containsKey(collage)){
                    // unlock included images 
                    lock_list.remove(collage);
                }
                // if the collage is accpeted by all
                else if(status.equals("true")){
                    // remove included images from working directory 
                    for(String img:lock_list.get(collage)) {
                        File file = new File(img);
                        if(file.exists()){
                            file.delete();
                        }
                        remove_list.add(img);
                    }
                    // unlock included images
                    lock_list.remove(collage);
                }
            }
        }
    }
    
    public static void main ( String args[] ) throws Exception {
        if (args.length != 2) throw new Exception("Need 2 args: <port> <id>");
        UserNode UN = new UserNode(args[1]);

        // read the log and recover from failure
        log_name = myId+".log";
        List<String> logs = logHandler.readLog(log_name);
        recoverFailure(logs);

        PL = new ProjectLib( Integer.parseInt(args[0]), args[1], UN);

        while(true){
        }
    }

}