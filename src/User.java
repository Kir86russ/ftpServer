public class User {

    private String userName;
    private String password;

    private Long connectionId;

    public User(Long connectionId) {
        this.connectionId = connectionId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Long getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(Long connectionId) {
        this.connectionId = connectionId;
    }

    @Override
    public String toString() {
        return "User{" +
                "  userName='" + userName + '\'' +
                ", password='" + password + '\'' +
                ", connectionId=" + connectionId +
                '}';
    }
}
