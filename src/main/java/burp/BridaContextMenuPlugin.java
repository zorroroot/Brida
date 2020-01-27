package burp;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JMenuItem;

import org.apache.commons.lang3.ArrayUtils;

public class BridaContextMenuPlugin extends CustomPlugin implements IContextMenuFactory {
	
	public IContextMenuInvocation currentInvocation;

	public BridaContextMenuPlugin(BurpExtender mainPlugin, String customPluginName, String customPluginExportedFunctionName,
			CustomPluginExecuteOnValues customPluginExecuteOn, String customPluginExecuteOnContextName,
			CustomPluginParameterValues customPluginParameter,
			String customPluginParameterString, CustomPluginEncodingValues customPluginParameterEncoding,
			CustomPluginFunctionOutputValues customPluginFunctionOutput, String customPluginFunctionOutputString,
			CustomPluginEncodingValues customPluginOutputEncoding,
			CustomPluginEncodingValues customPluginOutputDecoding) {
		super(mainPlugin, customPluginName, customPluginExportedFunctionName, customPluginExecuteOn, customPluginExecuteOnContextName,
				null, null, customPluginParameter,
				customPluginParameterString, customPluginParameterEncoding, customPluginFunctionOutput,
				customPluginFunctionOutputString, customPluginOutputEncoding, customPluginOutputDecoding);

		this.setType(CustomPlugin.CustomPluginType.ICONTEXTMENU);
		
	}
	
	@Override
	public String exportPlugin() {
		
		String result = "";
		
		result = result + getType().ordinal() + ";";
		
		result = result + Base64.getEncoder().encodeToString(getCustomPluginName().getBytes()) + ";";
		result = result + Base64.getEncoder().encodeToString(getCustomPluginExportedFunctionName().getBytes()) + ";";
		result = result + getCustomPluginExecuteOn().ordinal() + ";";
		result = result + Base64.getEncoder().encodeToString(getCustomPluginExecuteOnContextName().getBytes()) + ";";
		result = result + getCustomPluginParameter().ordinal() + ";";
		result = result + Base64.getEncoder().encodeToString(getCustomPluginParameterString().getBytes()) + ";";
		result = result + getCustomPluginParameterEncoding().ordinal() + ";";		
		result = result + getCustomPluginFunctionOutput().ordinal() + ";";
		result = result + Base64.getEncoder().encodeToString(getCustomPluginFunctionOutputString().getBytes()) + ";";
		result = result + getCustomPluginOutputEncoding().ordinal() + ";";
		result = result + getCustomPluginOutputDecoding().ordinal();
				
		return result;
		
	}
	
	@Override
	public void enable() {
		getMainPlugin().callbacks.registerContextMenuFactory(this);
		setOnOff(true);
	}

	@Override
	public void disable() {
		getMainPlugin().callbacks.removeContextMenuFactory(this);
		setOnOff(false);
	}

	@Override
	public List<JMenuItem> createMenuItems(IContextMenuInvocation invocation) {
		
		if(invocation.getInvocationContext() == IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_REQUEST ||
		   invocation.getInvocationContext() == IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_RESPONSE || 
		   invocation.getInvocationContext() == IContextMenuInvocation.CONTEXT_MESSAGE_VIEWER_REQUEST || 
		   invocation.getInvocationContext() == IContextMenuInvocation.CONTEXT_MESSAGE_VIEWER_RESPONSE) {
					
			currentInvocation = invocation;
			
			List<JMenuItem> menu = new ArrayList<JMenuItem>();
			
			JMenuItem itemBridaPlugin = new JMenuItem(getCustomPluginExecuteOnContextName());
			itemBridaPlugin.addActionListener(new ActionListener() {
	        	public void actionPerformed(ActionEvent actionEvent) {
					executeAction();
	        	}
			});		
			
			menu.add(itemBridaPlugin);
			
			return menu;	
			
		} else {
			
			return null;
			
		}

	}
	
	public void executeAction() {

		// If all is enabled
    	if(getMainPlugin().serverStarted && getMainPlugin().applicationSpawned) {
		
			String[] parameters;
			
			IHttpRequestResponse[] selectedItems = currentInvocation.getSelectedMessages();
			byte selectedInvocationContext = currentInvocation.getInvocationContext();
			
			byte[] selectedRequestOrResponse = null;
			boolean isRequest;
			
			if(selectedInvocationContext == IContextMenuInvocation.CONTEXT_MESSAGE_VIEWER_REQUEST || selectedInvocationContext == IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_REQUEST) {
				selectedRequestOrResponse = selectedItems[0].getRequest();
				isRequest = true;
			} else {
				selectedRequestOrResponse = selectedItems[0].getResponse();
				isRequest = false;
			}
			
			if(getCustomPluginParameter() == CustomPluginParameterValues.CONTEXT) {
				
				int[] selectedBounds = currentInvocation.getSelectionBounds();
				byte[] selectedPortion = Arrays.copyOfRange(selectedRequestOrResponse, selectedBounds[0], selectedBounds[1]);
				parameters = new String[] { encodeCustomPluginValue(selectedPortion,getCustomPluginParameterEncoding()) } ;
				
			} else {
				
				parameters = getParametersCustomPlugin(selectedRequestOrResponse,isRequest);
				
			}	
			
			String ret = callFrida(parameters);
			
			// DEBUG print
			printToExternalDebugFrame("*** START ***\n\n");
			printToExternalDebugFrame("** Original " + (isRequest ? "request" : "response") + "\n");
			printToExternalDebugFrame(new String(selectedRequestOrResponse));
			printToExternalDebugFrame("\n\n");
			if(parameters.length > 0) {
				printToExternalDebugFrame("** Frida parameters (after encoding)\n");
				for(int i=0;i<parameters.length;i++) {
					printToExternalDebugFrame("* Parameter " + (i+1) + ": " + parameters[i] + "\n");
				}
				printToExternalDebugFrame("\n\n");
			} else {
				printToExternalDebugFrame("** NO Frida parameters\n\n");
			}
			printToExternalDebugFrame("** Frida returned value (after deconding/encoding), printed in the tab\n");
			printToExternalDebugFrame(ret);
			printToExternalDebugFrame("\n\n");
			
			if(getCustomPluginFunctionOutput() == CustomPluginFunctionOutputValues.BRIDA) {
				
				getMainPlugin().printSuccessMessage("* Brida exported function " + getCustomPluginExportedFunctionName() + " output: " + ret);
				
				// DEBUG print
				printToExternalDebugFrame("** Output to Brida console\n\n");
				printToExternalDebugFrame("*** END ***\n\n");
				
			} else if(getCustomPluginFunctionOutput() == CustomPluginFunctionOutputValues.REGEX) {
				
				if(currentInvocation.getInvocationContext() == IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_REQUEST ||
				   currentInvocation.getInvocationContext() == IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_RESPONSE) {	
				
					Pattern patternCustomPlugin = Pattern.compile(getCustomPluginFunctionOutputString());
					Matcher matcherCustomPlugin = patternCustomPlugin.matcher(new String(selectedRequestOrResponse));
					if(matcherCustomPlugin.find()) {									
						String replacedRequestResponse = new StringBuilder(new String(selectedRequestOrResponse)).replace(matcherCustomPlugin.start(1), matcherCustomPlugin.end(1), ret).toString();
						
						// DEBUG print
						printToExternalDebugFrame("** Modified " + (isRequest ? "request" : "response") + " (with REGEX) \n");
						printToExternalDebugFrame(replacedRequestResponse);
						printToExternalDebugFrame("** \n\n");
						printToExternalDebugFrame("*** END ***\n\n");
						
						if(isRequest) {
							selectedItems[0].setRequest(replacedRequestResponse.getBytes());
						} else {
							selectedItems[0].setResponse(replacedRequestResponse.getBytes());
						}
						
					} else {
						getMainPlugin().printException(null,"No match in supplied output REGEX. Outputting to Brida console.");
						getMainPlugin().printSuccessMessage("* Brida exported function " + getCustomPluginExportedFunctionName() + " output: " + ret);
						
						// DEBUG print
						printToExternalDebugFrame("** Output to Brida console because REGEX did not match\n\n");
						printToExternalDebugFrame("*** END ***\n\n");
					}	
					
				} else {
					
					getMainPlugin().printException(null,"Can't replace a non-editable content. Outputting to Brida console.");
					getMainPlugin().printSuccessMessage("* Brida exported function " + getCustomPluginExportedFunctionName() + " output: " + ret);
					
					// DEBUG print
					printToExternalDebugFrame("** Output to Brida console because the message is NON-editable\n\n");
					printToExternalDebugFrame("*** END ***\n\n");
					
				}				
					
			} else if(getCustomPluginFunctionOutput() == CustomPluginFunctionOutputValues.CONTEXT) {
				
				int[] selectedBounds = currentInvocation.getSelectionBounds();
				
				if(selectedInvocationContext == IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_REQUEST || selectedInvocationContext == IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_RESPONSE) {
					
					byte[] preSelectedPortion = Arrays.copyOfRange(selectedRequestOrResponse, 0, selectedBounds[0]);
					byte[] postSelectedPortion = Arrays.copyOfRange(selectedRequestOrResponse, selectedBounds[1], selectedRequestOrResponse.length);
					byte[] newRequestResponse = ArrayUtils.addAll(preSelectedPortion, ret.getBytes());
					newRequestResponse = ArrayUtils.addAll(newRequestResponse, postSelectedPortion);
					
					// DEBUG print
					printToExternalDebugFrame("** Modified " + (isRequest ? "request" : "response") + " (CONTEXT mode) \n");
					printToExternalDebugFrame(new String(newRequestResponse));
					printToExternalDebugFrame("\n\n");
					printToExternalDebugFrame("*** END ***\n\n");
					
					selectedItems[0].setRequest(newRequestResponse);
					
				} else {
					
					getMainPlugin().printException(null,"Can't replace a non-editable content. Outputting to Brida console.");
					getMainPlugin().printSuccessMessage("* Brida exported function " + getCustomPluginExportedFunctionName() + " output: " + ret);
					
					// DEBUG print
					printToExternalDebugFrame("** Output to Brida console because the message is NON-editable\n\n");
					printToExternalDebugFrame("*** END ***\n\n");
					
				}								
			}
						
    	} else {
    		
    		getMainPlugin().printException(null, "Context Brida plugin not callable. Start pyro and spwan application first.");
    		
    	}
		
	}

}
