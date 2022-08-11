import * as vscode from 'vscode';
import { extensionInstance } from './core/extension';

export function activate(context: vscode.ExtensionContext) {
	
	extensionInstance.setContext(context);

	extensionInstance.init().catch((error)=> {
		console.log("Failed to activate Polyglot extension. " + (error));
	})

}

export function deactivate() {
	if (extensionInstance.getClient === undefined) {
		return undefined;
	}
	return extensionInstance.getClient()?.stop();
}
