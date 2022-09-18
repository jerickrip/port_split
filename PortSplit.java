package com.ni2.script;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.ni2.cmdb.common.ManagedEntityValue;
import com.ni2.cmdb.common.ManagedEntityValueIterator;
import com.ni2.cmdb.configuration.ContainsAssociationValue;
import com.ni2.cmdb.configuration.DeviceValue;
import com.ni2.cmdb.configuration.EndedByAssociationValue;
import com.ni2.cmdb.configuration.FunctionValue;
import com.ni2.cmdb.configuration.ImplementedByAssociationValue;
import com.ni2.cmdb.configuration.InterfaceValue;
import com.ni2.cmdb.configuration.LinkValue;
import com.ni2.cmdb.core.CMDBException;
import com.ni2.cmdb.core.CatalogItemValue;
import com.ni2.cmdb.core.ItemService;
import com.ni2.cmdb.core.LocalQueryService;
import com.ni2.cmdb.workflow.WorkflowService;

public class PortSplit extends AbstractSampleScript {

	private ItemService is;
	private LocalQueryService le;
	private HashMap<String, ManagedEntityValue> typeMapm = new HashMap<>();
	private HashMap<String, ManagedEntityValue> typeMapl = new HashMap<>();
	private HashMap<ManagedEntityValue, String> mlMap2 = new HashMap<>();
	private HashMap<ManagedEntityValue, ManagedEntityValue> pathMap = new HashMap<>();
	private HashMap<ManagedEntityValue, String> endportMap = new HashMap<>();
	private HashMap<ManagedEntityValue, String> xconnMap = new HashMap<>();
	private HashMap<ManagedEntityValue, String> lidMap = new HashMap<>();
	private HashMap<ManagedEntityValue, String> midMap = new HashMap<>();
	private HashMap<String, String> MaxValueMap = new HashMap<>();

	public static void main(String[] args) throws CMDBException {
		new PortSplit().run(args);
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
		System.out.println("-- PortSplit -- Start");
		this.is = getCMDB().getItemService();
		System.out.println("-- PortSplit -- Start PortSplit");
		PortSplit();
		System.out.println("-- PortSplit -- Final Commit PortSplit");
		getCMDB().getPrincipalTransaction().commit();
		getCMDB().closePrincipalTransaction();
		System.out.println("-- PortSplit -- End");
	}

	private void PortSplit() throws CMDBException {
		Map<String, Object> nqlParams = new HashMap<String, Object>();
		// create local engine
		String leNQL = ":mediaInterfaces = get with specs, intermediate Interface(\"Media Interface/Connector\") associated as role z of Contains with (get Device(\"Device/Equipment/Network Equipment/Passive/FIBER PATCH PANEL\",\"Device/Module/Network Module/Card/FIBER PANEL\",\"Device/Equipment/Network Equipment/Passive/SPLICE POINT\")) recursively where Name contains \",\" ;" //
				+ ":functionInterfaces = get with specs, intermediate Interface(\"Function Interface\") associated as role a of ImplementedBy with :mediaInterfaces ;"
				+ ":devices = get with specs, intermediate Device associated as role a of Contains with :mediaInterfaces ; "
				+ ":nodes = get with specs, intermediate Function(\"Function\") associated as role a of Contains with :functionInterfaces ; "
				+ ":path = get with specs, intermediate Link(\"Data Link\") associated as role a of EndedBy with :functionInterfaces ; "
				+ ":xconn = get with specs, intermediate Link(\"Media Link\") associated as role a of EndedBy with :mediaInterfaces; "
				+ ":endedbypath = get with specs, intermediate EndedByAssociation where InterfaceKey in :functionInterfaces and LinkKey in :path; "
				+ ":endedbyxconn = get with specs, intermediate EndedByAssociation where InterfaceKey in :mediaInterfaces and LinkKey in :xconn; "
				+ ":result += :functionInterfaces;" + ":result += :devices;" + ":result += :nodes;"
				+ ":result += :path;" + ":result += :xconn;" + ":result += :endedbypath;" + ":result += :endedbyxconn;";
		ManagedEntityValueIterator leMevs = this.is.query(leNQL, nqlParams);

		System.out.println("-- PortSplit -- Done building local engine - leMevs size: " + leMevs.getRemainingSize());
		this.le = this.is.createLocalNQLEngine(leMevs);

		// gather types in map
		this.typeMapm = new HashMap<String, ManagedEntityValue>();
		String typeNqlm = "get InterfaceType(\"Media Interface/Connector\") where Status == \"Operational\" ";
		List<ManagedEntityValue> typeMevm = this.le.query(typeNqlm);

		for (ManagedEntityValue type : typeMevm) {
			String typeNamem = (String) type.getManagedEntityKey().toString();
			this.typeMapm.put(typeNamem, type);
		}

		this.typeMapl = new HashMap<String, ManagedEntityValue>();
		String typeNqll = "get InterfaceType(\"Function Interface/Logical Port/Generic\") where Status == \"Operational\" ";
		List<ManagedEntityValue> typeMevl = this.le.query(typeNqll);

		for (ManagedEntityValue type : typeMevl) {
			String typeNamel = (String) type.getManagedEntityKey().toString();
			this.typeMapl.put(typeNamel, type);
		}
		// iterate over media interface
		ManagedEntityValue fi;
		int i = 1;
		List<ManagedEntityValue> mediaInterfaceList = this.le
				.query("get Interface(\"Media Interface/Connector\") where Name contains \",\"");
		int count = mediaInterfaceList.size();
		System.out.println("-- PortSplit -- total media Interfaces is: " + count);
		System.out.println("-- ************* -- Adding maximum values of end ports");
		int endportVal1 = 0;
		int endportVal2 = 0;
		ManagedEntityValueIterator circuitList = this.is.query(
				"get Link(\"Data Link\") associated as role a of EndedBy with (get Interface(\"Function Interface\") where Name contains \",\" ) ;");
		for (ManagedEntityValue cl : circuitList) {
			nqlParams.put(":path", cl);
			String pathNQL = "get EndedByAssociation where LinkKey in :path ;";
			ManagedEntityValueIterator endedbyList = this.is.query(pathNQL, nqlParams);
			if (endedbyList != null) {
				for (ManagedEntityValue el : endedbyList) {
					if (el.getAttributeValue("EndPort") != null) {
						if (!Pattern.compile("(1)|(99999)|(99998)|(50)")
								.matcher((String) el.getAttributeValue("EndPort")).matches()) {
							endportVal1 = (Integer) Integer.valueOf(el.getAttributeValue("EndPort").toString());
							if (this.MaxValueMap.containsKey((String) cl.getAttributeValue("Identifier"))) {
								endportVal2 = (Integer) Integer.valueOf(
										(String) this.MaxValueMap.get((String) cl.getAttributeValue("Identifier")));
								if (endportVal1 > endportVal2) {
									this.MaxValueMap.put((String) cl.getAttributeValue("Identifier"),
											(String) String.valueOf(endportVal1));
								}
							} else {
								this.MaxValueMap.put((String) cl.getAttributeValue("Identifier"),
										(String) String.valueOf(endportVal1));
							}
						}
					}
				}
				
				if(this.MaxValueMap.containsKey((String) cl.getAttributeValue("Identifier"))) {
					System.out.println("-- PortSplit -- Partial Commit: " + i + " path: "
							+ (String) cl.getAttributeValue("Identifier") + ", end port: "
							+ (String) this.MaxValueMap.get((String) cl.getAttributeValue("Identifier")));
					i++;					
				}
			}
		}

		System.out.println("-- ************* -- Iteration where new connector and logical ports are created, with their associations to other items");
		i = 1;
		for (ManagedEntityValue mi : mediaInterfaceList) {
			if (i % 500 == 0) {
				System.out.println("\n\n-- PortSplit -- Partial Commit: " + i + " out of " + count);
				getCMDB().getPrincipalTransaction().commit();
			}
			nqlParams.put(":mi", mi);
			String fiNQL = "get Interface(\"Function Interface\") associated as role a of ImplementedBy with :mi ;";
			fi = this.le.queryFirst(fiNQL, nqlParams);

			if (fi != null) {
				try {
					this.createMirrorPort(mi, fi, i);
					i++;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		try {
			System.out.println("-- ************* -- Associating new media links to new ports ");
			i = 1;
			HashMap<String, Object> attributesx = new HashMap<>();
			nqlParams.put(":xctype", "Wired");
			ManagedEntityValue xctype = this.le.queryFirst("get LinkType(\"Media Link/Cable\") where Name==:xctype",
					nqlParams);
			Set<ManagedEntityValue> set_xc = xconnMap.keySet();
			for (ManagedEntityValue xckey : set_xc) {
				String xcValue = xconnMap.get(xckey);
				String[] xconn_array1;
				String IdentifierPortA = "";
				String IdentifierPortZ = "";
				String EndPortA = "";
				String EndPortZ = "";
				String UniversalIdA = "";
				String UniversalIdZ = "";
				String NewXConnectId = "";
				ManagedEntityValue xc_port1 = null;
				ManagedEntityValue xcmev = null;
				if (!xcValue.contains("_")) {
					xconn_array1 = xcValue.split(";");
					IdentifierPortA = xconn_array1[0];
					EndPortA = xconn_array1[2];
					String miNql1 = "get Interface(\"Media Interface\") where Identifier==\"" + IdentifierPortA	+ "\"; ";
					xc_port1 = this.is.queryFirst(miNql1);
					if (xc_port1 != null) {
						if (xc_port1.getAttributeValue("UniversalId") != null) {
							UniversalIdA = (String) xc_port1.getAttributeValue("UniversalId").toString().replace("PPO-",
									"");
						} else {
							UniversalIdA = (String) xc_port1.getAttributeValue("Identifier").toString().replace("IFC-",
									"");
						}
					}
					attributesx.put("Name", "XC-" + UniversalIdA);
					attributesx.put("UniversalId", "XCO-" + UniversalIdA);
					xcmev = this.is.createInstanceItem((CatalogItemValue) xctype, attributesx);
				} else {
					xconn_array1 = xcValue.split("_");
					String[] array_A = xconn_array1[0].split(";");
					String[] array_Z = xconn_array1[1].split(";");
					IdentifierPortA = array_A[0];
					IdentifierPortZ = array_Z[0];
					EndPortA = array_A[2];
					EndPortZ = array_Z[2];
					String miNql1 = "get Interface(\"Media Interface\") where Identifier==\"" + IdentifierPortA	+ "\"; ";
					xc_port1 = this.is.queryFirst(miNql1);
					if (xc_port1 != null) {
						if (xc_port1.getAttributeValue("UniversalId") != null) {
							UniversalIdA = (String) xc_port1.getAttributeValue("UniversalId").toString().replace("PPO-","");
						} else {
							UniversalIdA = (String) xc_port1.getAttributeValue("Identifier").toString().replace("IFC-","");
						}
					}
					String miNql2 = "get Interface(\"Media Interface\") where Identifier==\"" + IdentifierPortZ
							+ "\"; ";
					ManagedEntityValue xc_port2 = this.is.queryFirst(miNql2);
					if (xc_port2 != null) {
						if (xc_port2.getAttributeValue("UniversalId") != null) {
							UniversalIdZ = (String) xc_port2.getAttributeValue("UniversalId").toString().replace("PPO-","");
						} else {
							UniversalIdZ = (String) xc_port2.getAttributeValue("Identifier").toString().replace("IFC-","");
						}
					}
					NewXConnectId = UniversalIdA + "_" + UniversalIdZ;
					attributesx.put("Name", "XC-" + NewXConnectId);
					attributesx.put("UniversalId", "XCO-" + NewXConnectId);
					xcmev = this.is.createInstanceItem((CatalogItemValue) xctype, attributesx);
					if (xc_port2 != null) {
						ManagedEntityValue endedByxc2 = this.is.createAssociation((LinkValue) xcmev,
								(InterfaceValue) xc_port2, EndedByAssociationValue.class.getName());
						endedByxc2.setAttributeValue("EndPort", EndPortZ);
						this.is.updateItem(endedByxc2);
						String print_xc2;
						if (xc_port2.getAttributeValue("UniversalId") != null) {
							print_xc2="-- PortSplit -- " + i + " -- X-Connect: "
									+ (String) xcmev.getAttributeValue("UniversalId") + " EndedBy Mirror Port: "
									+ (String) xc_port2.getAttributeValue("UniversalId") + ". EndPort: " + EndPortZ;
						} else {
							print_xc2="-- PortSplit -- " + i + " -- X-Connect: "
									+ (String) xcmev.getAttributeValue("Identifier") + " EndedBy Mirror Port: "
									+ (String) xc_port2.getAttributeValue("Identifier") + ". EndPort: " + EndPortZ;
						}
						System.out.println(print_xc2);						
					}
				}

				if (xc_port1 != null) {
					ManagedEntityValue endedByxc1 = this.is.createAssociation((LinkValue) xcmev,
							(InterfaceValue) xc_port1, EndedByAssociationValue.class.getName());
					endedByxc1.setAttributeValue("EndPort", EndPortA);
					this.is.updateItem(endedByxc1);
					String print_xc1;
					if (xc_port1.getAttributeValue("UniversalId") != null) {
						print_xc1="-- PortSplit -- " + i + " -- X-Connect: "
								+ (String) xcmev.getAttributeValue("UniversalId") + " EndedBy Mirror Port: "
								+ (String) xc_port1.getAttributeValue("UniversalId") + ". EndPort: " + EndPortA;
					} else {
						print_xc1="-- PortSplit -- " + i + " -- X-Connect: "
								+ (String) xcmev.getAttributeValue("Identifier") + " EndedBy Mirror Port: "
								+ (String) xc_port1.getAttributeValue("Identifier") + ". EndPort: " + EndPortA;
					}
					System.out.println(print_xc1);
				}
				i++;
				getCMDB().getPrincipalTransaction().commit();
			}

			System.out.println("-- ************* -- Renaming old media links that are associated to old ports");
			i = 1;
			Set<ManagedEntityValue> setMl2 = mlMap2.keySet();
			for (ManagedEntityValue me : setMl2) {
				String xcKey = "";
				ManagedEntityValue xcmev = (ManagedEntityValue) me;
				xcKey = (String) mlMap2.get(me);
				xcmev.setAttributeValue("Name", "XC-" + xcKey);
				this.is.updateItem(xcmev);
				xcmev.setAttributeValue("UniversalId", "XCO-" + xcKey);
				this.is.updateItem(xcmev);
				if (xcmev.getAttributeValue("UniversalId") != null) {
					System.out.println("-- PortSplit -- " + i + " -- Renamed X-Connect: "
							+ (String) xcmev.getAttributeValue("UniversalId"));
				} else {
					System.out.println("-- PortSplit -- " + i + " -- Renamed X-Connect: "
							+ (String) xcmev.getAttributeValue("Identifier"));
				}
				i++;
				getCMDB().getPrincipalTransaction().commit();
			}

			System.out.println("-- ************* -- Associating path to new ports ");
			Map<String, Object> endPortParams = new HashMap<String, Object>();
			i = 1;
			Set<ManagedEntityValue> set_path = pathMap.keySet();
			for (ManagedEntityValue me_lp : set_path) {
				ManagedEntityValue me_ckt = pathMap.get(me_lp);
				String assocEndPort = endportMap.get(me_lp);
				endPortParams.put("EndPort", assocEndPort);
				this.is.createAssociation((LinkValue) me_ckt, (InterfaceValue) me_lp,
						EndedByAssociationValue.class.getName(), endPortParams);
				getCMDB().getPrincipalTransaction().commit();
				System.out.println("-- PortSplit -- " + i + " -- Path: "
						+ (String) me_ckt.getAttributeValue("Identifier") + " EndedBy Mirror Port: "
						+ (String) me_lp.getAttributeValue("Identifier") + " - " + assocEndPort);
				i++;
			}

			System.out.println("-- ************* -- Updating Name and UniversalId attribute on logical ports");
			i = 1;
			Set<ManagedEntityValue> set_lid = lidMap.keySet();
			for (ManagedEntityValue mel : set_lid) {
				String ref_id = lidMap.get(mel);
				String[] strVal1 = ref_id.split("_");
				String lp_name = strVal1[1];
				String lp_uid = strVal1[0];
				mel.setAttributeValue("UniversalId", lp_uid);
				mel.setAttributeValue("Name", lp_name);
				this.is.updateItem(mel);
				System.out.println("-- PortSplit -- " + i + " -- Path: " + (String) mel.getAttributeValue("Identifier")
				+ " - " + (String) mel.getAttributeValue("UniversalId") + " - "
				+ (String) mel.getAttributeValue("Name"));
				i++;
				getCMDB().getPrincipalTransaction().commit();
			}

			System.out.println("-- ************* -- Updating Name and UniversalId attribute on connector ports");
			i = 1;
			Set<ManagedEntityValue> set_mid = midMap.keySet();
			for (ManagedEntityValue mep : set_mid) {
				String ref_id = midMap.get(mep);
				String[] strVal2 = ref_id.split("_");
				String pp_name = strVal2[1];
				String pp_uid = strVal2[0];
				mep.setAttributeValue("UniversalId", pp_uid);
				mep.setAttributeValue("Name", pp_name);
				this.is.updateItem(mep);
				System.out.println("-- PortSplit -- " + i + " -- Path: " + (String) mep.getAttributeValue("Identifier")
				+ " - " + (String) mep.getAttributeValue("UniversalId") + " - "
				+ (String) mep.getAttributeValue("Name"));
				i++;
				getCMDB().getPrincipalTransaction().commit();
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			e.printStackTrace();
		}
	}

	private void createMirrorPort(ManagedEntityValue mi, ManagedEntityValue fi, int i) throws CMDBException {
		Map<String, Object> nqlParams = new HashMap<String, Object>();
		HashMap<String, Object> attributesm = new HashMap<>();
		HashMap<String, Object> attributesl = new HashMap<>();
		StringBuilder sb1 = new StringBuilder();
		StringBuilder sb_ao = new StringBuilder();
		ManagedEntityValue logPort = null;
		ManagedEntityValue phyPort = null;
		ManagedEntityValue node = null;
		ManagedEntityValue device = null;
		ManagedEntityValue path = null;
		ManagedEntityValue xconn = null;
		ManagedEntityValue endedby = null;
		ManagedEntityValue endedbyxconn = null;

		String lid = "";
		String pid = "";
		String rateCode = "";
		String SplittedName[] = null;
		String endPort = "";
		String xconnendPort = "";
		String xconnUniversalId = "";

		if (mi.getAttributeValue("RateCode") != null) {
			rateCode = (String) mi.getAttributeValue("RateCode");
			rateCode = rateCode.replace("/", "-").replace("\"", "in.");
			attributesm.put("RateCode", rateCode);
		} else {
			rateCode = "";
		}

		if (mi.getAttributeValue("Status") != null) {
			attributesm.put("Status", (String) mi.getAttributeValue("Status"));
		}

		if (mi.getAttributeValue("Description") != null) {
			attributesm.put("Description", (String) mi.getAttributeValue("Description"));
		}

		if (mi.getAttributeValue("RCNPortAccessId") != null) {
			attributesm.put("RCNPortAccessId", (String) mi.getAttributeValue("RCNPortAccessId"));
		}

		if (mi.getAttributeValue("RCNConnectorType") != null) {
			attributesm.put("RCNConnectorType", (String) mi.getAttributeValue("RCNConnectorType"));
		}

		if (mi.getAttributeValue("RCNPathChangeDate") != null) {
			attributesm.put("RCNPathChangeDate", (Date) mi.getAttributeValue("RCNPathChangeDate"));
		}

		attributesl.putAll(attributesm);

		if (mi.getAttributeValue("RCNAWiredPort") != null) {
			attributesm.put("RCNAWiredPort", (String) mi.getAttributeValue("RCNAWiredPort"));
		}

		if (mi.getAttributeValue("RCNZWiredPort") != null) {
			attributesm.put("RCNZWiredPort", (String) mi.getAttributeValue("RCNZWiredPort"));
		}

		if (mi.getAttributeValue("ServiceStatus") != null) {
			attributesm.put("ServiceStatus", (String) mi.getAttributeValue("ServiceStatus"));
		}

		ManagedEntityValue connLogicalPortType = this.typeMapl.get((String)fi.getAttributeValue("DefiningTypeKey").toString());
		if (connLogicalPortType == null) {
			connLogicalPortType = this.typeMapl.get("Generic Logical Port");
		}

		ManagedEntityValue connPhysicalPortType = this.typeMapm.get((String)mi.getAttributeValue("DefiningTypeKey").toString());
		if (connPhysicalPortType == null) {
			connPhysicalPortType = this.typeMapm.get("Generic Connector");
		}

		String currentMIUID = "";
		String currentMIIdentifier = (String) mi.getAttributeValue("Identifier");
		String currentLPUID = "";
		String currentLPIdentifier = (String) fi.getAttributeValue("Identifier");
		String assgnOrder = "";
		String new_assgnOrder = "";
		String strEndPort = "";
		String assignmentStatus = (String) fi.getAttributeValue("AssignmentStatus");
		String connectivityStatus = (String) mi.getAttributeValue("ConnectivityStatus");
		String PhyName = (String) mi.getAttributeValue("Name");

		currentLPUID = (fi.getAttributeValue("UniversalId") != null) ? (String) fi.getAttributeValue("UniversalId")	: "";

		nqlParams.put(":fi", fi);
		node = this.le.queryFirst("get Function(\"Function\") associated as role a of Contains with :fi ;", nqlParams);
		path = this.is.queryFirst("get Link(\"Data Link\") associated as role a of EndedBy with :fi ;", nqlParams);

		if (path != null) {
			if (path.getAttributeValue("AssignmentOrder") != null) {
				assgnOrder = (String) path.getAttributeValue("AssignmentOrder").toString();
				if (!assgnOrder.equals("")) {
					if (assgnOrder.contains(currentLPUID)) {
					}
					if (assgnOrder.contains(currentLPIdentifier)) {
					}
				}
			}
			nqlParams.put(":path", path);
			String pathNQL = "get EndedByAssociation where InterfaceKey in :fi and LinkKey in :path ;";
			endedby = this.le.queryFirst(pathNQL, nqlParams);
		}

		currentMIUID = (mi.getAttributeValue("UniversalId") != null) ? (String) mi.getAttributeValue("UniversalId")	: "";
		nqlParams.put(":mi", mi);
		device = this.le.queryFirst("get Device(\"Device\") associated as role a of Contains with :mi ;", nqlParams);
		xconn = this.le.queryFirst("get Link(\"Media Link/Cable\") associated as role a of EndedBy with :mi;",
				nqlParams);
		if (xconn != null) {
			nqlParams.put(":xconn", xconn);
			String xconnNQL = "get EndedByAssociation where InterfaceKey in :mi and LinkKey in :xconn ;";
			endedbyxconn = this.le.queryFirst(xconnNQL, nqlParams);
			if (endedbyxconn != null) {
				xconnendPort = (String) endedbyxconn.getAttributeValue("EndPort");
				if (xconnendPort.equals("") || xconnendPort.equals(" ") || xconnendPort == null) {
					xconnendPort = "50";
				}
			}
		}

		if (currentLPUID.equals("")) {
			sb1.append(" -- Ports " + currentMIIdentifier + ", " + currentLPIdentifier);
		} else {
			sb1.append(" -- Ports " + currentMIUID + ", " + currentLPUID);
		}

		sb1.append(", \"" + PhyName + "\" ==> ");
		if (PhyName.contains(",")) {
			SplittedName = ParseName(PhyName);
			int flag1 = 1;
			long flag2 = SplittedName.length;
			for (String strName : SplittedName) {
				lid = (currentLPUID == null || currentLPUID.equals(""))
						? "LPO-" + currentLPIdentifier + "-" + strName.trim()
						: "LPO-" + currentLPUID.replace("LPO-", "") + "-" + strName.trim();
				pid = (currentMIUID == null || currentMIUID.equals(""))
						? "PPO-" + currentMIIdentifier + "-" + strName.trim()
						: "PPO-" + currentMIUID.replace("PPO-", "") + "-" + strName.trim();

				if (flag1 == 1) {
					this.lidMap.put(fi, lid + "_" + strName);
					this.midMap.put(mi, pid + "_" + strName);

					if (xconn != null) {
						if (xconn.getAttributeValue("UniversalId") != null) {
							xconnUniversalId = (String) xconn.getAttributeValue("UniversalId").toString()
									.replace("XCO-", "");
							String new_xconnUniversalId = "";
							String new_pid = currentMIUID.replace("PPO-", "");
							if (mlMap2.containsKey(xconn)) {
								Set<ManagedEntityValue> set_xconn = mlMap2.keySet();
								for (ManagedEntityValue me : set_xconn) {
									String result = (String) mlMap2.get(me);
									if (result.contains(new_pid)) {
										new_xconnUniversalId = result.replace(new_pid, new_pid + "-" + strName.trim());
										this.mlMap2.put(xconn, new_xconnUniversalId);
										break;
									}
								}
							} else {
								if (xconnUniversalId.contains(new_pid)) {
									new_xconnUniversalId = xconnUniversalId.replace(new_pid,
											new_pid + "-" + strName.trim());
								}
								this.mlMap2.put(xconn, new_xconnUniversalId);
							}
						}
					}
				} else {
					try {
						attributesl.put("Name", strName.trim());
						attributesl.put("UniversalId", lid);
						attributesl.put("AssignmentStatus", assignmentStatus);
						logPort = this.is.createInstanceItem((CatalogItemValue) connLogicalPortType, attributesl);

						if (path != null) {
							if (endedby.getAttributeValue("EndPort") != null) {
								if (!Pattern.compile("(1)|(99999)|(99998)|(50)")
										.matcher((String) endedby.getAttributeValue("EndPort")).matches()) {
									if (this.MaxValueMap.containsKey((String) path.getAttributeValue("Identifier"))) {
										Set<String> set_maxvalue = this.MaxValueMap.keySet();
										for (String memv : set_maxvalue) {
											if (memv.equals((String) path.getAttributeValue("Identifier"))) {
												int int_ep1 = (Integer) Integer.valueOf(this.MaxValueMap.get(memv));
												int int_ep2 = int_ep1 + 1;
												this.MaxValueMap.put(memv, String.valueOf(int_ep2));
												strEndPort = String.valueOf(int_ep2);
												break;
											}
										}
									}
								} else {
									if (endPort.equals("99999")) {
										strEndPort = "59";
									}
									if (endPort.equals("99998")) {
										strEndPort = "58";
									}
									if (endPort.equals("1")) {
										strEndPort = "51";
									}
									if (endPort.equals("50")) {
										strEndPort = "50";
									}
								}
								this.pathMap.put(logPort, path);
								this.endportMap.put(logPort, strEndPort);
							}
							getCMDB().getPrincipalTransaction().commit();
						}

						if (node != null) {
							this.is.createAssociation((FunctionValue) node, (InterfaceValue) logPort,
									ContainsAssociationValue.class.getName());
						}

						attributesm.put("Name", strName.trim());
						attributesm.put("UniversalId", pid);
						attributesm.put("ConnectivityStatus", connectivityStatus);
						phyPort = this.is.createInstanceItem((CatalogItemValue) connPhysicalPortType, attributesm);

						if (xconn != null) {
							String xc_Value = "";
							if (xconnMap.containsKey(xconn)) {
								String new_xconnValue = "";
								xc_Value = xconnMap.get(xconn);
								new_xconnValue = xc_Value + "_" + (String) phyPort.getAttributeValue("Identifier") + ";"
										+ strName.trim() + ";" + xconnendPort;
								xconnMap.put(xconn, new_xconnValue);
							} else {
								xc_Value = (String) phyPort.getAttributeValue("Identifier") + ";" + strName.trim() + ";"
										+ xconnendPort;
								xconnMap.put(xconn, xc_Value);
							}
						}

						if (device != null) {
							this.is.createAssociation((DeviceValue) device, (InterfaceValue) phyPort,
									ContainsAssociationValue.class.getName());
						}

						this.is.createAssociation((InterfaceValue) logPort, (InterfaceValue) phyPort,
								ImplementedByAssociationValue.class.getName());
					} catch (NullPointerException e) {
						e.printStackTrace();
					}
				}

				if (strEndPort.equals("")) {
					sb1.append(lid + "," + pid);
				} else {
					sb1.append(lid + ", EndPort: " + strEndPort + "," + pid);
				}

				String str_ao="";
				if(assgnOrder.contains("Identifier:")) {
					str_ao=(flag1 == 1)?currentLPIdentifier:(String) logPort.getAttributeValue("Identifier");
				}else {
					str_ao=(flag1 == 1)?lid:(String) logPort.getAttributeValue("UniversalId");
				}
				
				sb_ao.append(str_ao);
				if (flag1 < flag2) {
					sb1.append(",");
					sb_ao.append("-//-");
				}

				flag1++;
			}

			if (path != null) {
				new_assgnOrder=(assgnOrder.contains("Identifier:")) ? assgnOrder.replace(currentLPIdentifier, sb_ao.toString()) : assgnOrder.replace(currentLPUID,sb_ao.toString());				
				path.setAttributeValue("AssignmentOrder", new_assgnOrder);
				this.is.updateItem(path);
				sb1.append("\n	"+(String) path.getAttributeValue("Identifier")+". Old AssignmentOrder: "+ assgnOrder + "\n	"+(String) path.getAttributeValue("Identifier")+". New AssignmentOrder: " +(String)path.getAttributeValue("AssignmentOrder"));
			}
			System.out.println("-- PortSplit -- " + i + sb1);
		}

	}

	static String[] ParseName(String PhyName) {
		boolean b = false;
		String SplittedName[] = null;
		String pName;

		if (PhyName.contains(",") && b == false) {
			if (Pattern.compile("([:digit:]*[,][:digit:]*)[A-Za-z]*").matcher(PhyName).matches() && b == false) {
				SplittedName = PhyName.split(",");
				pName = SplittedName[0] + (String) SplittedName[1].replaceAll("[^A-Za-z]", "");
				PhyName = SplittedName[0] + (String) SplittedName[1].replaceAll("[^A-Za-z]", "") + ","
						+ SplittedName[1];
				b = true;
			}

			if (!b && Pattern.compile("[:digit:]*[/][:digit:]*[,][:digit:]*[/][:digit:]*").matcher(PhyName).matches()
					&& b == false) {
				SplittedName = PhyName.split(",");
				PhyName = SplittedName[0] + "," + SplittedName[1];
				b = true;
			}

			if (Pattern.compile("[:digit:]*[,][:digit:]*[ ]([:alpha:]*)").matcher(PhyName).matches() && b == false) {
				SplittedName = PhyName.split(",");
				String sn1[] = SplittedName[1].split("(");
				PhyName = SplittedName[0] + " (" + sn1[1] + "," + SplittedName[1];
				b = true;
			}

			if (!b && Pattern.compile("[:digit:]*[A-Za-z]*[,][:digit:]*").matcher(PhyName).matches() && b == false) {
				SplittedName = PhyName.split(",");
				pName = (String) SplittedName[0].replaceAll("[^A-Za-z]", "") + SplittedName[1];
				PhyName = SplittedName[0] + "," + SplittedName[1]
						+ (String) SplittedName[0].replaceAll("[^A-Za-z]", "");
				b = true;
			}

			if (!b && Pattern.compile("[A-Za-z]*[-][:digit:]*[ ][(]([:digit:]*[,][:digit:]*)[)]").matcher(PhyName)
					.matches() && b == false) {
				SplittedName = PhyName.split(",");
				pName = (String) SplittedName[0].replaceAll("[ ][(][:digit:]*", "");
				PhyName = pName + " " + (String) SplittedName[0].replaceAll("[A-Za-z]*[-][:digit:]*[ ][(]", "") + ","
						+ pName + " " + SplittedName[1].replaceAll("[)]", "");
				b = true;
			}

			if (!b && Pattern.compile("[:digit:]*[,][:digit:]*").matcher(PhyName).matches() && b == false) {
				SplittedName = PhyName.split(",");
				PhyName = SplittedName[0] + "," + SplittedName[1];
				b = true;
			}

			if (!b && Pattern.compile("([:digit:]*[,][:digit:]*)").matcher(PhyName).matches() && b == false) {
				SplittedName = PhyName.split(",");
				PhyName = (String) SplittedName[0].replaceAll("[(]", "") + "," + SplittedName[1].replaceAll("[)]", "");
				b = true;
			}
			SplittedName = PhyName.split(",");
		}
		return SplittedName;
	}
}
