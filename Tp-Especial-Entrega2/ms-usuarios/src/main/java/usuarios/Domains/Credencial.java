package usuarios.Domains;

// Lo minimo para validar login y armar el token. El hash nunca sale en una vista.
public record Credencial(long id, String rol, String passwordHash) {}
