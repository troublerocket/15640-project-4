import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/* CollageProcess class for processing photo collages */
public class CollageProcess{
    // the filename of the collage
    public String filename = null;
    // the contents of the collage
    public byte[] contents =null;
    // users included in the collage
    public Set<String> user_list  = new HashSet<>();
    // users whose vote is received
    public Set<String> vote_list  = new HashSet<>();
    // users whose ack is received
    public Set<String> ack_list = new HashSet<>();

    // vote gathering status
    public boolean decision_made = false;
    // decision making status
    public boolean final_decision = false;
    // the current status
    public String status = null;

    // all replies are gathered and the decision is made
    private final static  String Decision_step = "Decision";
    // all ack are received and the collage is committed
    private final static  String Committed_step = "Committed";
    
    private static String logName = "Server.log";

    // the sources images
    private String[] sources = null;
    // the messages the collage needs to send to users
    private Map<String, NewMessage> message_list = new HashMap<>();
    // the ProjectLib instance
    private  ProjectLib PL = null;

    // timer for vote message timeout
    private Timer voteTimer = new Timer();
    // timer for ack message timeout
    private Timer ackTimer = new Timer();

    /**
     * CollageProcess constructor
     * 
     * @param Pl ProjectLib instance
     * @param Filename filename of the collage
     * @param Contents contents of the collage
     * @param Sources sources of the collage
     */
    public CollageProcess(ProjectLib Pl, String Filename, 
                          byte[] Contents, String[] Sources){

        this.PL = Pl;
        this.filename = Filename;
        this.contents = Contents;
        this.sources = Sources;
        user_list = new HashSet<>();

        for(String source:this.sources){
            // parse the source parameter into user and image name
            String user = source.split(":")[0];
            user_list.add(user);
            String image = source.split(":")[1];
            
            if(message_list.containsKey(user)==false){
                NewMessage msg = new NewMessage(filename, contents, 
                                                user, image, 1);
                message_list.put(user,msg);
            }
            else{
                message_list.get(user).addImg(image);
            }
        }
    }

    /**
     * send message to every usernode to ask for its vote
     */
    public void gatherVote(){

        for(String user:user_list){
            byte[] body = logHandler.serialize(message_list.get(user));
            ProjectLib.Message msg = new ProjectLib.Message(user, body);
            PL.sendMessage(msg);
            message_list.remove(user);
        }
        // count the vote time
        countVotetime();
    }

    /**
     * distribute the decision of the collage to every usernode
     * 
     * @param decision true if the collage is approved, false otherwise
     */
    public void distributeDecision(boolean decision){

        for(String user:user_list){
            if(ack_list.contains(user)){// the user already acked
                continue;
            }
            NewMessage message = new NewMessage(filename,user,decision,3);
            byte[] body = logHandler.serialize(message);
            ProjectLib.Message msg = new ProjectLib.Message(user,body);
            PL.sendMessage(msg);
        }
        decision_made = true;

    }

    /**
     * count in the ack received from the usernode
     * 
     * @param user id of the usernode
     * @return true if it is acknowledged by all usernodes, false otherwise
     */
    public boolean countAck(String user){

        // wrong user
        if(user_list.contains(user) == false){
            return false;
        }
        // record the ack
        ack_list.add(user);
        
        if(user_list.size() == ack_list.size()){// all ack received            
            return true;
        }
        return false;
    }

    /**
     * count in the vote received from the usernode
     * 
     * @param user id the usernode
     * @return true if it is approved by all usernodes, false otherwise
     */
    public boolean countVote(String user){

        // wrong user
        if(user_list.contains(user) == false){
            return false;
        }
        // record the vote
        vote_list.add(user);
    
        if(user_list.size() == vote_list.size()){// all votes received
            return true;
        }
        return false;
    }

    /**
     * commit the approved collage
     * 
     * @param filename the filename of the collage
     * @param img the image contents of the collage
     */
    private static void commitCollage(String filename, byte[] img) {
        try {
            // write the images to the working directory 
            Files.write(new File(filename).toPath(), img);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * receive and handle the message from usernode
     * 
     * @param message the message received
     * @return true if the collage is done, false otherwise
     */
    public boolean getMessage(NewMessage message){

        String user = message.addr;
        boolean vote = message.vote;

        // ack message from user
        if(message.type == 4){
            if(countAck(user)==true){// all ack received and collage committed
                // stop ack timer
                ackTimer.cancel();
                // write log for the committed step
                logHandler.writeLog(PL,logName,Committed_step+";"+filename);
                return true;
            }
            return false;
        }

        // decision distributing
        if(decision_made){
            return false;
        }

        // vote message from user
        if(vote == true){// vote is yes

            if(countVote(user) == true){ // all votes received

                // write the files to the working directory
                commitCollage(filename, contents);

                final_decision = true;
                // stop vote timer
                voteTimer.cancel();
                // write log
                logHandler.writeLog(PL,logName,
                                    Decision_step+";"+filename+";true");
                // distribute the decision
                distributeDecision(final_decision);
                // start ack timer
                countAcktime();
            }
        }
        else{// vote is no

            final_decision = false;
            voteTimer.cancel();
            logHandler.writeLog(PL,logName,Decision_step+";"+filename+";false");
            distributeDecision(final_decision);
            countAcktime();
        }
        return false;
    }
    
    /**
     * count time for lost message asking for vote
     */
    public void countVotetime(){
        voteTimer.schedule(new voteTimerTask(),3000,3000);
    }
    
    /**
     * count time for lost message asking for ack
     */
    public void countAcktime(){
        ackTimer.schedule(new ackTimerTask(),3000,3000);
    }

    /* voteTimerTask class for counting vote message time */
    private class voteTimerTask extends TimerTask{

        public voteTimerTask(){
        }
    
        @Override
        public void run(){
            voteTimer.cancel();
            if(decision_made==false){
                // if vote timeout, abort the collage
                logHandler.writeLog(PL,logName,
                                    Decision_step+";"+filename+";false");
                final_decision = false;
                distributeDecision(final_decision);
                countAcktime();
            }
        }
    } 

    /* ackTimerTask class for counting ack message time */
    private class ackTimerTask extends TimerTask{
    
        public  ackTimerTask(){
        }
    
        @Override
        public void run(){
            // if ack timeout, redistribute the decision
            distributeDecision(final_decision);
        }
    }
}