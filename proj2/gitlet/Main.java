package gitlet;

import static gitlet.Repo.REPO;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author MockCanine
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            abort("Please enter a command.");
        }
        String firstArg = args[0];
        if (!firstArg.equals("init")) {
            REPO.isInRepo();
        }
        switch (firstArg) {
            case "init":
                validateNumArgs(args, 1);
                Repo.init();
                break;
            case "add":
                validateNumArgs(args, 2);
                Repo.add(args[1]);
                break;
            case "commit":
                validateNumArgs(args, 2);
                Repo.commit(args[1]);
                break;
            case "rm":
                validateNumArgs(args, 2);
                Repo.rm(args[1]);
                break;
            case "log":
                validateNumArgs(args, 1);
                Repo.log();
                break;
            case "global-log":
                validateNumArgs(args, 1);
                Repo.globalLog();
                break;
            case "find":
                validateNumArgs(args, 2);
                Repo.find(args[1]);
                break;
            case "status":
                validateNumArgs(args, 1);
                Repo.status();
                break;
            case "checkout":
                Repo.checkout(args);
                break;
            case "branch":
                validateNumArgs(args, 2);
                Repo.branch(args[1]);
                break;
            case "rm-branch":
                validateNumArgs(args, 2);
                Repo.rmBranch(args[1]);
                break;
            case "reset":
                validateNumArgs(args, 2);
                Repo.reset(args[1]);
                break;
            case "merge":
                validateNumArgs(args, 2);
                Repo.merge(args[1]);
                break;
            case "add-remote":
                validateNumArgs(args, 3);
                Repo.addRemote(args[1], args[2]);
                break;
            case "rm-remote":
                validateNumArgs(args, 2);
                Repo.rmRemote(args[1]);
                break;
            case "push":
                validateNumArgs(args, 3);
                Repo.push(args[1], args[2]);
                break;
            case "fetch":
                validateNumArgs(args, 3);
                Repo.fetch(args[1], args[2]);
                break;
            case "pull":
                validateNumArgs(args, 3);
                Repo.pull(args[1], args[2]);
                break;
            default:
                abort("No command with that name exists.");
        }
    }

    /**
     * Checks the number of arguments versus the expected number,
     * Print message and exit program if not match
     */
    public static void validateNumArgs(String[] args, int n) {
        if (args.length != n) {
            abort("Incorrect operands.");
        }
    }

    /**
     * Print message and abort program
     */
    public static void abort(String msg) {
        Utils.message(msg);
        System.exit(0);
    }
}
