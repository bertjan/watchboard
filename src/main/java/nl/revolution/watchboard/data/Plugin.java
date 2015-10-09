package nl.revolution.watchboard.data;

public class Plugin {

    private Graph.Type type;
    private String username;
    private String password;
    private String loginUrl;

    public Graph.Type getType() {
        return type;
    }

    public void setType(Graph.Type type) {
        this.type = type;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getLoginUrl() {
        return loginUrl;
    }

    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
    }

}
