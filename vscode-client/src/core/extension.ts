import path = require("path");
import * as vscode from "vscode";
import { ChildProcess, spawn } from 'mz/child_process';
import * as net from 'net';
import {
  LanguageClientOptions,
  RevealOutputChannelOn,
} from "vscode-languageclient";
import {
  LanguageClient,
  ServerOptions,
  State,
  StreamInfo,
} from "vscode-languageclient/node";

const outputChannel : vscode.OutputChannel = vscode.window.createOutputChannel("Polyglot");
const LS_LAUNCHER_MAIN: string = "PolyglotLanguageServerLauncher";

export class PolyglotExtension {
  private languageClient?: LanguageClient;
  private context?: vscode.ExtensionContext;

  getClient() : LanguageClient | undefined{
    return this.languageClient;
  }

  setContext(context: vscode.ExtensionContext) {
    this.context = context;
  }

  async init(): Promise<void> {
    try {
      outputChannel.appendLine("Extension initialization ...");
      //Server options. LS client will use these options to start the LS.
      let serverOptions: ServerOptions = getServerOptions(outputChannel);

      //creating the language client.
      let clientId = "polyglot-vscode-lsclient";
      let clientName = "Polyglot LS Client";
      let clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: "file", language: "python" }, { scheme: "file", language: "javascript" }], //, 
        outputChannel: outputChannel,
        revealOutputChannelOn: RevealOutputChannelOn.Never
      };
      this.languageClient = new LanguageClient(
        clientId,
        clientName,
        serverOptions,
        clientOptions
        );
        
        const disposeDidChange = this.languageClient.onDidChangeState(
          (stateChangeEvent) => {
            if (stateChangeEvent.newState === State.Stopped) {
              vscode.window.showErrorMessage(
                "Failed to initialize the extension"
              );
            } else if (stateChangeEvent.newState === State.Running) {
              outputChannel.appendLine("Extension initialized successfully!");
          }
        }
      );
      this.languageClient.start().then(() => {
        disposeDidChange.dispose();
      });
      
    } catch (exception) {
      return Promise.reject("Extension error!");
    }
  }
}

//Create a command to be run to start the LS java process.
function getServerOptions(outputChannel : vscode.OutputChannel) {
  //Change the project home accordingly.
  const LS_LIB = path.resolve(__filename+"/../../../../PolyglotLanguageServer-1.0-SNAPSHOT-jar-with-dependencies.jar")

  let executable: string = path.join("java");
  let args: string[] = ["-cp", LS_LIB];

  const tcpServerOptions = () =>
			new Promise<ChildProcess | StreamInfo>((resolve, reject) => {

				const server = net.createServer(socket => {
					server.close()
					resolve({ reader: socket, writer: socket })
				});

				server.listen(2088, '127.0.0.1', () => {
					const childProcess = spawn(executable, [...args, LS_LAUNCHER_MAIN]);
					childProcess.on('exit', (code, signal) => {
						if (code !== 0) {
							outputChannel.show()
						}
					});
					return childProcess
				});
			});

  return tcpServerOptions;
}

export const extensionInstance = new PolyglotExtension();