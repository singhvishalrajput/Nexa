public class SetupDatabaseTest {
  private static int checks;
  static void rejected(Runnable check) {
    try { check.run(); throw new AssertionError("Unsafe input was accepted"); }
    catch (IllegalArgumentException expected) { checks++; }
  }
  public static void main(String[] args) {
    if (!SetupDatabase.schema("NEXA_APP").equals("NEXA_APP")) throw new AssertionError();
    for (String value : new String[]{"SYS", "SYSTEM", "NEXA_X; DROP USER SYS", "NEXA_\"X", "NEXA_", "NEXA_" + "A".repeat(26)})
      rejected(() -> SetupDatabase.schema(value));
    rejected(() -> SetupDatabase.identifier("USERS QUOTA UNLIMITED"));
    for (String value : new String[]{"", "short", "secret\"password", "secret\npassword"})
      rejected(() -> SetupDatabase.password(value, true));
    SetupDatabase.password("LocalSetup@12345", true);
    SetupDatabase.password("existing-short", false);
    for (String value : new String[]{"CDB$ROOT", "PDB$SEED", "", null})
      rejected(() -> SetupDatabase.requirePdb(value));
    SetupDatabase.requirePdb("FREEPDB1");
    System.out.println("Passed " + (checks + 4) + " setup safety checks; no database connection made.");
  }
}
