package erp.common.security;

public enum Role {
    EMPLOYEE,
    APPROVER,
    ADMIN;

    public String toAuthority() {
        return "ROLE_" + name();
    }
}
