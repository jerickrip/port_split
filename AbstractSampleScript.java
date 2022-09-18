package com.ni2.script;

import com.ni2.cmdb.core.Environment;
import com.ni2.cmdb.core.CMDBException;
import com.ni2.cmdb.core.CMDBConnector;
import com.ni2.config.Ni2BaseConfig;
import com.ni2.cmdb.core.CMDB;

public abstract class AbstractSampleScript
{
    private String user;
    private String password;
    private String tenant;
    private String[] scriptArgs;
    private CMDB cmdb;
    
    public AbstractSampleScript() {
        this.user = null;
        this.password = null;
        this.tenant = null;
        this.scriptArgs = new String[0];
        this.cmdb = null;
    }
    
    public void run(final String[] args) {
        try {
            if (this.parseArgs(args)) {
                this.execute(this.scriptArgs);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    protected abstract void execute(final String[] p0) throws Exception;
    
    public boolean parseArgs(final String[] args) {
        if (args.length > 1) {
            this.user = args[0];
            System.out.println("user = " + this.user);
            this.password = args[1];
            if (args.length > 2) {
                this.tenant = args[2];
            }
            if (this.tenant == null || this.tenant.compareToIgnoreCase("default") == 0 || this.tenant.compareToIgnoreCase("null") == 0) {
                this.tenant = new Ni2BaseConfig().getDefaultInventoryName();
            }
            if (args.length >= 3) {
                this.scriptArgs = copyArgs(args, 3);
            }
            return true;
        }
        this.help();
        return false;
    }
    
    public static String[] copyArgs(final String[] args, final int pos) {
        final String[] scriptArgs = new String[args.length - pos];
        for (int i = pos; i < args.length; ++i) {
            scriptArgs[i - pos] = args[i];
            System.out.println("scriptArgs[" + (i - pos) + "] = " + scriptArgs[i - pos]);
        }
        return scriptArgs;
    }
    
    protected CMDB getCMDB() throws CMDBException {
        if (this.cmdb == null) {
            final Ni2BaseConfig config = new Ni2BaseConfig();
            this.cmdb = CMDBConnector.getService().login(this.user, this.password);
            final Environment globalInventory = (Environment)this.cmdb.getEnvironmentService().findInventoryByName(config.getDefaultInventoryName());
            this.cmdb.activateEnvironment(globalInventory);
            final Environment env = this.cmdb.getEnvironmentService().findEnvironmentByName(this.tenant);
            if (env == null) {
                throw new CMDBException("No environment[" + this.tenant + "] for user[" + this.user + "]");
            }
            this.cmdb.activateEnvironment(env);
        }
        return this.cmdb;
    }
    
    protected void help() {
    }
}

