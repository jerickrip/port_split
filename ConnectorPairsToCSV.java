package com.ni2.script;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.io.PrintWriter;

import com.ni2.cmdb.common.*;
import com.ni2.cmdb.core.CMDBException;
import com.ni2.cmdb.core.ItemService;
import com.ni2.cmdb.core.LocalQueryService;
import com.ni2.cmdb.workflow.WorkflowService;


public class ConnectorPairsToCSV extends AbstractSampleScript {

	private ItemService is;
	private LocalQueryService le;
	public HashMap<String, ManagedEntityValue> typeMap;

	public static void main(String[] args) {
		new ConnectorPairsToCSV().run(args);
	}

	public void runWorkflow(WorkflowService ws) throws CMDBException {
		try {
		} catch (Exception e) {
			getCMDB().getPrincipalTransaction().rollback();
			e.printStackTrace();
		}
	}

	@Override
	public void execute(String[] args) throws Exception {
		System.out.println("-- ConnectorPairsToCSV -- Start");
		this.is = getCMDB().getItemService();
		// Create mirror logical ports for ports that do not have one
		System.out.println("-- ConnectorPairsToCSV -- Start ConnectorPairsToCSV");
		ConnectorPairsToCSV();		
		System.out.println("-- ConnectorPairsToCSV -- Final Commit ConnectorPairsToCSV");
		getCMDB().getPrincipalTransaction().commit();
		getCMDB().closePrincipalTransaction();
		System.out.println("-- ConnectorPairsToCSV -- End");
	}

	private void ConnectorPairsToCSV() throws CMDBException {
		// create local engine
		String leNQL = ":mediaInterfaces = get with specs, intermediate Interface(\"Media Interface/Connector\") associated as role z of Contains with (get Device(\"Device/Equipment/Network Equipment/Passive/FIBER PATCH PANEL\")) recursively where Name contains \",\" and UniversalId != \"\" ;"
						+ ":functionInterfaces = get with specs, intermediate Interface(\"Function Interface\") associated as role a of ImplementedBy with :mediaInterfaces where Name contains \",\" and UniversalId != \"\";"
						+ ":devices = get with specs, intermediate Device associated as role a of Contains with :mediaInterfaces;"
						+ ":nodes = get with specs, intermediate Function(\"Function\") associated as role a of Contains with :functionInterfaces;"
						+ ":path = get with specs, intermediate Link(\"Data Link/Circuit\") associated as role a of EndedBy with :functionInterfaces;"						
						+ ":result = get InterfaceType(\"Function Interface\");"
						+ ":result += get InterfaceType(\"Media Interface\");"
						+ ":result += get DeviceType(\"Device\");"
						+ ":result += :mediaInterfaces;"
						+ ":result += :functionInterfaces;"
						+ ":result += :devices;"
						+ ":result += :nodes;"
						+ ":result += :path;";
		ManagedEntityValueIterator leMevs = this.is.query(leNQL);
		System.out.println("-- ConnectorPairsToCSV -- Done building local engine - leMevs size: "+leMevs.getRemainingSize());
		this.le = this.is.createLocalNQLEngine(leMevs);

		// gather types in map
		this.typeMap =  new HashMap<String, ManagedEntityValue>();
		String typeNql = "get InterfaceType(\"Function Interface/Logical Port/Generic\",\"Media Interface/Connector\") where Status == \"Operational\" ";
		List<ManagedEntityValue> typeMevs = this.le.query(typeNql);

		String typeDevNql = "get DeviceType(\"Device\") where Status == \"Operational\" ";
		List<ManagedEntityValue> typeDevMevs = this.le.query(typeDevNql);
		
		String typeNodNql = "get FunctionType(\"Function\") where Status == \"Operational\" ";
		List<ManagedEntityValue> typeNodMevs = this.le.query(typeNodNql);
		
		String typeCirNql = "get LinkType(\"Data Link/Circuit\") where Status == \"Operational\" ";
		List<ManagedEntityValue> typeCirMevs = this.le.query(typeCirNql);
		
		String typeName="";
		for (ManagedEntityValue type : typeMevs) {
			typeName = (String) type.getAttributeValue("Name");
			//System.out.println("-- ConnectorPairsToCSV -- Done building local engine - leMevs size: "+leMevs.getRemainingSize());
			this.typeMap.put(typeName, type);
		}

/*
		for (ManagedEntityValue type : typeNodMevs) {
			typeName = (String) type.getAttributeValue("Name");
			//System.out.println("-- ConnectorPairsToCSV -- Done building local engine - leMevs size: "+leMevs.getRemainingSize());
			this.typeMap.put(typeName, type);
		}*/
		/*
		for (ManagedEntityValue type : typeCirMevs) {
			typeName = (String) type.getAttributeValue("Name");
			//System.out.println("-- ConnectorPairsToCSV -- Done building local engine - leMevs size: "+leMevs.getRemainingSize());
			this.typeMap.put(typeName, type);
		}
		*/
		// iterate over media interface
		Map<String, Object> nqlParams = new HashMap<String, Object>();
		int count = 1;
		ManagedEntityValue fi;
		String currentLPUID;
		String currentMIUID;
		String PhyName;
		String[] SplittedName=null;
		String sName;
		String sCategory;
		String conStr="";
		String lid = "";
		String pid = "";
		String line1 = "";
		String line2 = "";
		String line3 = "";
		String line4 = "";
		String line5 = "";
		String line6 = "";
		String strNode,strDevice="";
		String strLogicalPort,strPhysicalPort,strPath, rateCode;
		PrintWriter out1 = null;
		PrintWriter out2 = null;
		PrintWriter out3 = null;
		PrintWriter out4 = null;
		PrintWriter out5 = null;
		PrintWriter out6 = null;
		ManagedEntityValue node = null;
		ManagedEntityValue device = null;
		ManagedEntityValue path =null;

		try {
		    out1 = new PrintWriter(new FileWriter("/opt/ni2/jboss/server/rcn/add/Ni2Script-Migration-01/DATA01_ConnectorPair_LogicalPort-Instances.csv"));
		   out2 = new PrintWriter(new FileWriter("/opt/ni2/jboss/server/rcn/add/Ni2Script-Migration-01/DATA01_ConnectorPair_PhysicalPort-Instances.csv"));
		    out3 = new PrintWriter(new FileWriter("/opt/ni2/jboss/server/rcn/add/Ni2Script-Migration-01/DATA01_ConnectorPair_LogicalPort-ImplementedBy-PhysicalPort.csv"));
		    out4 = new PrintWriter(new FileWriter("/opt/ni2/jboss/server/rcn/add/Ni2Script-Migration-01/DATA01_ConnectorPair_LogicalPort-Contains-Node.csv"));
		    out5 = new PrintWriter(new FileWriter("/opt/ni2/jboss/server/rcn/add/Ni2Script-Migration-01/DATA01_ConnectorPair_PhysicalPort-Contains-Device.csv"));
		    out6 = new PrintWriter(new FileWriter("/opt/ni2/jboss/server/rcn/add/Ni2Script-Migration-01/DATA01_ConnectorPair_LogicalPort-EndedBy-Path.csv"));

		    } catch (IOException e) {
		            e.printStackTrace();
		    }
		/*out1.println("\"Category\",\"T.Name\",\"I.Name\",\"I.UniversalId\",\"I.RateCode\",\"I.Description\",\"I.RCNConnectorType\",\"I.Status\",\"I.RCNPathChangeDate\",\"I.RCNPortAccessId\",\"I.RCNAWiredPort\",\"I.RCNZWiredPort\"");
		out2.println("\"Category\",\"T.Name\",\"I.Name\",\"I.UniversalId\",\"I.RateCode\",\"I.Description\",\"I.RCNConnectorType\",\"I.Status\",\"I.RCNPathChangeDate\",\"I.RCNPortAccessId\",\"I.RCNAWiredPort\",\"I.RCNZWiredPort\"");*/
		out1.println("\"Category\",\"T.Name\",\"I.Name\",\"I.UniversalId\"");
		out2.println("\"Category\",\"T.Name\",\"I.Name\",\"I.UniversalId\"");
		out3.println("\"Category\",\"T.Name\",\"I.Name\",\"I.UniversalId\",\"REL.1.RelationName\",\"REL.1.Side\",\"REL.1.Category\",\"REL.1.Identifier\"");
		out4.println("\"Category\",\"T.Name\",\"I.Name\",\"I.UniversalId\",\"REL.1.RelationName\",\"REL.1.Side\",\"REL.1.Category\",\"REL.1.Identifier\"");
		out5.println("\"Category\",\"T.Name\",\"I.Name\",\"I.UniversalId\",\"REL.1.RelationName\",\"REL.1.Side\",\"REL.1.Category\",\"REL.1.Identifier\"");
		out6.println("\"Category\",\"T.Name\",\"I.Name\",\"I.UniversalId\",\"REL.1.RelationName\",\"REL.1.Side\",\"REL.1.Category\",\"REL.1.Identifier\"");

		int i = 1;
		List<ManagedEntityValue> mediaInterfaceList = this.le.query("get Interface(\"Media Interface/Connector\") where Name contains \",\" and UniversalId != \"\"");
		System.out.println("-- ConnectorPairsToCSV -- total media Interfaces is: "+mediaInterfaceList.size());
		for (ManagedEntityValue mi : mediaInterfaceList) {
						// commit every 500
			    		if (i%500==0){
			    				System.out.println("\n\n-- ConnectorPairsToCSV -- Partial Commit: "+i+" out of "+count);
			    				getCMDB().getPrincipalTransaction().commit();
			    			}
						String ptype = (String) mi.getAttributeValue("RCNConnectorType");
	        			// get logports implementedby
	        			nqlParams.put(":mi", mi);
	        			fi = this.le.queryFirst("get Interface(\"Function Interface\") associated as role a of ImplementedBy with :mi where Name contains \",\" and UniversalId != \"\"", nqlParams);
	        			if (fi !=null) {
	        				currentLPUID = (String) fi.getAttributeValue("UniversalId");
	        				currentMIUID = (String) mi.getAttributeValue("UniversalId");
	        				if(currentLPUID != null && currentMIUID != null){
	        					currentLPUID = currentLPUID.substring(4);
	        					currentMIUID = currentMIUID.substring(4);
	        					if(currentLPUID.equals(currentMIUID)){
	        						// split the mirror port into separated instances
	        							try {
	        								PhyName=(String) mi.getAttributeValue("Name");
		        								if(Pattern.compile("([0-9]*[,][0-9]*)[A-Za-z]*").matcher(PhyName).matches()){
		        					        		SplittedName=PhyName.split(",");
		        					        		sName=SplittedName[0]+(String)SplittedName[1].replaceAll("[^A-Za-z]","");
		        					        		PhyName=SplittedName[0]+(String)SplittedName[1].replaceAll("[^A-Za-z]","")+","+SplittedName[1];
		        					            }else if(Pattern.compile("[A-Za-z]*[-][0-9]*[ ][(]([0-9]*[,][0-9]*)[)]").matcher(PhyName).matches()){
		        					        		SplittedName=PhyName.split(",");
		        					        		sName=(String)SplittedName[0].replaceAll("[ ][(][0-9]*","");
		        					        		PhyName=sName+" "+(String)SplittedName[0].replaceAll("[A-Za-z]*[-][0-9]*[ ][(]","")+","+sName+" "+SplittedName[1].replaceAll("[)]","");
		        					            }else {
		        					        		System.out.println("-- ConnectorPairsToCSV -- The name of the instance "+(String) mi.getAttributeValue("UniversalId")+" -- "+PhyName);
		        					            }
		        								
		        								typeName="";
		        								SplittedName =PhyName.split(",");
		        								for (String strName : SplittedName) {

			        								lid = (String) fi.getAttributeValue("UniversalId")+"-"+strName;
			        								pid = (String) mi.getAttributeValue("UniversalId")+"-"+strName;
			        								rateCode = (String) mi.getAttributeValue("RateCode");
			        								rateCode = rateCode.replace("/", "-").replace("\"", "in.");
			        								// get type from map	
			        								ManagedEntityValue connLogicalPortType = this.typeMap.get(rateCode);
			        								if (connLogicalPortType == null) {
			        									connLogicalPortType = this.typeMap.get("Generic Logical Port");
			        								}
			        										
			        								ManagedEntityValue connPhysicalPortType = this.typeMap.get(ptype);
			        								if (connPhysicalPortType == null) {
			        									connPhysicalPortType = this.typeMap.get("Generic Physical Port");
			        								}
			        								/*
			        								ManagedEntityValue connDeviceType = this.typeMap.get(ptype);
			        								if (connPhysicalPortType == null) {
			        									connPhysicalPortType = this.typeMap.get("Generic Physical Port");
			        								}*/
			        					    		nqlParams.put(":fi", fi);
			        					    		node = this.le.queryFirst("get Function(\"Function\") associated as role a of Contains with :fi ", nqlParams);
			        					    		device = this.le.queryFirst("get Device(\"Device\") associated as role a of Contains with :mi  ", nqlParams);
			        					    		path = this.le.queryFirst("get Link(\"Data Link/Circuit\") associated as role a of EndedBy with :fi ", nqlParams);
			        					    		/*String Description=(String) mi.getAttributeValue("Description");			        					    		
			        					    		String RCNConnectorType=(String) mi.getAttributeValue("RCNConnectorType");
			        					    		String Status=(String) mi.getAttributeValue("Status");
			        					    		String RCNPathChangeDate=(String) mi.getAttributeValue("RCNPathChangeDate");
			        					    		String RCNPortAccessId=(String) mi.getAttributeValue("RCNPortAccessId");
			        					    		String RCNAWiredPort= (String) mi.getAttributeValue("RCNAWiredPort");
			        					    		String RCNZWiredPort=(String) mi.getAttributeValue("RCNZWiredPort");*/
			        								//line1 ="\"LOGICAL_PORT\",\""+rateCode+"\",\""+strName+"\",\""+lid+"\",\""+rateCode+"\",\""+Description+"\",\""+RCNConnectorType+"\",\""+Status+"\",\""+RCNPathChangeDate+"\",\""+RCNPortAccessId+"\",\""+RCNAWiredPort+"\",\""+RCNZWiredPort+"\"";
			        								line1 ="\"LOGICAL_PORT\",\""+rateCode+"\",\""+strName+"\",\""+lid+"\"";
			        								out1.println(line1);
			        								System.out.print("\n\n"+pid+" "+line1);
			        								
			        								//line2 = "\"CONNECTOR\",\""+ptype+"\",\""+strName+"\",\""+pid+"\",\""+rateCode+"\",\""+Description+"\",\""+RCNConnectorType+"\",\""+Status+"\",\""+RCNPathChangeDate+"\",\""+RCNPortAccessId+"\",\""+RCNAWiredPort+"\",\""+RCNZWiredPort+"\"";
			        								line2 = "\"CONNECTOR\",\""+ptype+"\",\""+strName+"\",\""+pid+"\"";
			        								out2.println(line2);
			        								System.out.print("\n\n"+pid+" "+line2);
			        								
			        								line3 = "\"LOGICAL_PORT\",\""+rateCode+"\",\""+strName+"\",\""+lid+"\",\"ImplementedBy\",\"Z\",\"CONNECTOR\",\""+pid+"\"";
			        								out3.println(line3);
			        								System.out.print("\n\n"+pid+" "+line3);
			        								
			        								if (node!=null) {
			        									for (ManagedEntityValue type : typeNodMevs) {
			        										if(type.getManagedEntityKey().toString().equals(node.getAttributeValue("DefiningTypeKey").toString())) {
				        										typeName = (String) type.getAttributeValue("Name");
					        									//System.out.print(" ----- "+type.getManagedEntityKey().toString()+" "+device.getAttributeValue("DefiningTypeKey").toString()+" "+typeName);
																//this.typeMap.put(typeName, type);
			        										}			        											
		        										}
				        								line4 = "\"LOGICAL_PORT\",\""+rateCode+"\",\""+strName+"\",\""+lid+"\",\"Contains\",\"A\",\"ND-"+typeName+"\",\""+(String)node.getAttributeValue("UniversalId")+"\"";
				        								out4.println(line4);
				        								System.out.print("\n\n"+pid+" "+line4);			        									
			        								}
			        								
			        								if (device!=null) {	        									
			        									for (ManagedEntityValue type : typeDevMevs) {
				        										if(type.getManagedEntityKey().toString().equals(device.getAttributeValue("DefiningTypeKey").toString())) {
					        										typeName = (String) type.getAttributeValue("Name");
						        									//System.out.print(" ----- "+type.getManagedEntityKey().toString()+" "+device.getAttributeValue("DefiningTypeKey").toString()+" "+typeName);
																	//this.typeMap.put(typeName, type);
				        										}			        											
			        										}
				        								line5 = "\"CONNECTOR\",\""+ptype+"\",\""+strName+"\",\""+pid+"\",\"Contains\",\"A\",\""+typeName+"\",\""+(String)device.getAttributeValue("UniversalId")+"\"";
				        								out5.println(line5);
				        								System.out.print("\n\n"+pid+" "+line5);        									
			        								}

			        								if (path!=null) {
			        									switch (rateCode) {
			        									case "1GBE":
			        									case "5GBE":
			        									case "10GBE":
			        									case "40GBE":
			        									case "100GBE":
			        									case "ETHERNET":
			        									case "56kb":
			        										sCategory="CONNECTOR";
			        										break;
			        									default:
			        										sCategory="PATH";
			        										break;
			        									}
			        									line6 = "\"LOGICAL_PORT\",\""+rateCode+"\",\""+strName+"\",\""+lid+"\",\"EndedBy\",\"Z\",\""+sCategory+"\",\""+(String)path.getAttributeValue("UniversalId")+"\"";
				        								out6.println(line6);
				        								System.out.print("\n\n"+pid+" "+line6);		        									
			        								}
		        								
		        								i++;
	        								}
	        							} catch (Exception e) {
	        								e.printStackTrace();
	        							}	        						
	        					}else{
	        						System.out.println("-- ConnectorPairsToCSV -- Already handled Port : "+(String)mi.getAttributeValue("UniversalId"));
	        					}
	        				}
	        			} 
		        		//deleteMirrorPort(mi,  fi);
				}
			}

/*
	private void deleteMirrorPort(ManagedEntityValue mi, ManagedEntityValue fi) throws CMDBException {
		is.deleteItem(mi.getManagedEntityKey());
		is.deleteItem(fi.getManagedEntityKey());
		System.out.println("--- ConnectorPairsToCSV --- Deleted parent Physical Port: "+(String)mi.getAttributeValue("UniversalId")+". Deleted parent Logical Port: "+(String)fi.getAttributeValue("UniversalId"));	
	}	*/
/*
	private void executeCommand() throws CMDBException {
		//ProcessBuilder pb = new ProcessBuilder("/opt/ni2/jboss/server/rcn/add/Ni2Script-Migration-01/run_sample.sh", "Admin", "RCNd3m0#", "default", "./Migration");
		try {
		    InputStream is = Runtime.getRuntime().exec(new String[] {"/opt/ni2/jboss/server/rcn/add/Ni2Script-Migration-01/run_sample.sh", "Admin", "RCNd3m0#", "default", "./Migration"]).getInputStream();
		    int i = is.read();
		    while(i > 0) {
		        System.out.print((char)i+" -- "+is.toString());
		        i = is.read();
		    }
		} catch (IOException e) {
		    e.printStackTrace();
		}
	}	*/
}

