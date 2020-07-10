import java.io.Serializable;
import java.util.LinkedList;

/* NewMessage class for constructing the message between server and usernode */
public class NewMessage implements Serializable{

    private static final long serialVersionUID = 1L;
    // filename of the collage
    public String filename = null;
    // cotents of the collage
    public byte[] contents =null;
    // id of the user node
    public String addr = null;
    // filename of the images
    public LinkedList<String> imgs = new LinkedList<>();
    // the return value of the uservote
    public boolean vote = false;

    /* types of the massage */ 
    // 1 -> server asks user for vote
    // 2 -> user vote message
    // 3 -> server distributes the decision
    // 4 -> user ack message
    public int type = 1;


    /**
     * message from server to usernode constructor
     * 
     * @param fn filename of the collage
     * @param c contents of the collage
     * @param a id of the usernode
     * @param img filename of the images
     * @param t type of the message
     */
    public NewMessage(String fn, byte[] c, String a, String img, int t){
        this.filename = fn;
        this.contents = c;
        this.addr = a;
        this.imgs.add(img);
        this.type = t;
    }

    /**
     * message from usernode to server constructor
     * 
     * @param fn filename of the collage
     * @param a id of the usernode
     * @param v vote of the usernode
     * @param t type of the message
     */
    public NewMessage(String fn, String a, boolean v, int t){
        this.filename = fn;
        this.addr = a;
        this.vote = v;
        this.type = t;
    }

    /**
     * add required image for the usernode
     */
    public void addImg(String img){
        if(imgs.contains(img) == false) {
            this.imgs.add(img);
        }
    }

}