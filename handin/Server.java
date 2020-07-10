import java.util.*;

/* Server class */
public class Server implements ProjectLib.CommitServing {
    /* 3 steps of a server in 2PC */
    // satrt commit
    private final static  String Initial_step = "startCommit";
    // all replies are gathered and the decision is made
    private final static  String Decision_step = "Decision";
    // all ack are received and the collage is committed
    private final static  String Committed_step = "Committed";

    // ProjectLib instance
    private static ProjectLib PL;
    // records of all the collages posted to the server that need to committ
    private static Map<String,CollageProcess> collageCommit =
                                        new HashMap<String,CollageProcess>();
    // log file name
    private static String log_name = "Server.log";


    
    /**
     * post the new collage to the server and start commit
     * 
     * @param filename  file name of the collage
     * @param img  image contents of the collage
     * @param sources the sources of the collage
     */
    public void startCommit(String filename, byte[] img, String[] sources){
        // write log for the first step
        String log_content = parseCollage(filename, sources);
        logHandler.writeLog(PL, log_name, log_content);

        // initiailize the new collage and put it to the commit records
        CollageProcess newCollage = new CollageProcess(PL,filename,img,sources);
        collageCommit.put(filename, newCollage);

        // start a new thread for the collage
        collageRunnable newrun = new collageRunnable(newCollage);
        newrun.run();
    }

    /**
     * parse the collage command parameters
     * 
     * @param filename file name of the collage
     * @param sources the sources of the collage
     * @return the parsed collage parameters
     */
    public String parseCollage(String filename, String[] sources){

        StringBuffer content = new StringBuffer();
        content.append(Initial_step + ";");
        content.append(filename);

        for(String source:sources) {
            content.append(";" + source);
        }
        return content.toString();
    }

    /**
     * recover from failure based on the log file
     * 
     * @param logs the records in the log file
     */
    public static void recoverFailure(List<String> logs){
        
        if(logs == null){// no log and no collage commit yet
            return;
        }

        for(String line:logs){
            String[] content = line.split(";");
            // the current step to recover
            String step = content[0];
            // the filename of the collage
            String collage = content[1];
            // the collage instance
            CollageProcess currCollage = null;

            
            if(step.equals(Initial_step)){// recover from start commit 
                String[] sources = Arrays.copyOfRange(content,2,content.length);
                // reinitialize the collage
                currCollage = new CollageProcess(PL, collage, null, sources);
                currCollage.status = Initial_step;
                // put the collage into commit records
                collageCommit.put(collage, currCollage);
            }
            else if(step.equals(Decision_step)){// recover from decision made
                if(collageCommit.containsKey(collage)){
                    String status = content[2];
                    currCollage = collageCommit.get(collage);
                    // record the decision
                    currCollage.status = Decision_step;
                    currCollage.decision_made = true;
                    currCollage.final_decision = Boolean.parseBoolean(status);
                }
            }
            else if(step.equals(Committed_step)){// committed and no recovery
                if(collageCommit.containsKey(collage)){
                    // remove the collage from commit records
                    collageCommit.remove(collage);
                }
            }
        }
        // recommit all the collages
        recommitCollage();
    }

    /**
     * recommit all the collages in the commit records
     */
    public static void recommitCollage(){

        CollageProcess currCollage = null;
        for(String filename:collageCommit.keySet()){
            currCollage = collageCommit.get(filename);
            // failure before a decision is made
            if(currCollage.status.equals(Initial_step)){
                // abort the collage and distribute NO decision 
                currCollage.final_decision = false;
                currCollage.distributeDecision(false);
                currCollage.countAcktime();
            }
            // failure after the decision is made
            else if(currCollage.status.equals(Decision_step)){
                // redistribute the decision
                currCollage.distributeDecision(currCollage.final_decision);
                currCollage.countAcktime();
            }
        }
    }


    public static void main ( String args[] ) throws Exception {
        if (args.length != 1) throw new Exception("Need 1 arg: <port>");
        Server srv = new Server();
        PL = new ProjectLib( Integer.parseInt(args[0]), srv );

        // read the log and recover from failure
        List<String> logs = logHandler.readLog(log_name);
        recoverFailure(logs);

        // main loop
        while (true) {
            // receive message from usernodes
            ProjectLib.Message msg = PL.getMessage();
            NewMessage message = logHandler.deserialize(msg.body);
            String collage = message.filename;

            // ignore the collage not need to commit
            if(collageCommit.containsKey(collage) == false){
                continue;
            }
            

            CollageProcess currCollage = collageCommit.get(collage);

            if (currCollage.getMessage(message) == true){// collage committed
                collageCommit.remove(collage);
            }
        }
    }
    /* collageRunnable class for concurrent collage commit */
    class collageRunnable implements Runnable {

        CollageProcess newCollage;

        public collageRunnable(CollageProcess collage) {
            this.newCollage = collage;
        }

        @Override
        public void run() {
            newCollage.gatherVote();
        }
    }

}