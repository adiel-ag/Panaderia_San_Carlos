package gt.edu.umg.sancarlos.dto;

/** Credenciales enviadas a POST /api/auth/login. */
public class LoginDTO {

    private String username;
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
