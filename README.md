# :bookmark_tabs: Polyglot Language Server:
[GraalVM](https://www.graalvm.org/) is a JDK that brings Polyglot capability which allows *"multiple programming languages in a single application while eliminating foreign language call costs"*.

The drawback of developing such applications is that there are no tools for developers to debug or diagnose their polyglot program.

Polyglot Language Server is a prototype which provides diagnostics, type checking & auto-completion for Polyglot's GraalVM programs.

# :hammer: How to install :

### 1 - Polyglot AST :

### 2 - Polyglot Language Server : 

### 3 - Polyglot Language Client (vscode) :

# :closed_book: Features :

## Diagnostics : 

### Description  :
Diagnostics are information, warning or error messages represented with an underline effect.

![diagnostics_gif](readme/diagnostics.gif)

### Diagnostics handled :

| Diagnostic                | Description                                                                        | Type    |
|---------------------------|------------------------------------------------------------------------------------|---------|
| Evaluation File not found | Occurs if you wrote an polyglot evaluation of a file that doesn't exist            | Error   |
| Unused Export             | Occurs if you exported a variable but never imported it back.                      | Info    |
| Import Before Export      | Occurs if you import a variable that is exported after the import statement        | Warning |
| Import without Export     | Occurs if you import a variable that has never been exported in a polyglot context | Warning |
| Useless Variable Import   | Occurs if you import a variable that was exported previously from the same file    | Info    |
| Same File Evaluation      | Occurs if you write a polyglot evaluation of the file you are currently in         | Warning |

### How it works :

## Type Checking :
### Description  :
Type Checking gives you the possibility to get the type of variable that was imported from polyglot context.

![type_checking_gif](readme/type_checking.gif)

### How it works :

![type_checking_explanation](readme/Type%20Checking%20Explanation.png)

## Auto-Completion :

### Description :

Auto Completion is a little prototype feature which gives you the possibility to import any variable from the polyglot context that are still not imported in the current file.

![auto_completion_gif](readme/auto-completion.gif)

### How it works :

* 1 - Gather all variables that have been exported from all files part of the polyglot program into a list.
* 2 - Remove from the list all variables that are imported in the current file.
* 3 - Add completion item for each variable from the list, with the proper code depending on the current file programming language.