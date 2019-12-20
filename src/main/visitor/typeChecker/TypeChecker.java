package main.visitor.typeChecker;

import main.ast.node.*;
import main.ast.node.Program;
import main.ast.node.declaration.*;
import main.ast.node.declaration.handler.*;
import main.ast.node.declaration.VarDeclaration;
import main.ast.node.expression.*;
import main.ast.node.expression.operators.BinaryOperator;
import main.ast.node.expression.operators.UnaryOperator;
import main.ast.node.expression.values.BooleanValue;
import main.ast.node.expression.values.IntValue;
import main.ast.node.expression.values.StringValue;
import main.ast.node.statement.*;
import main.ast.type.Type;
import main.ast.type.arrayType.ArrayType;
import main.ast.type.noType.NoType;
import main.ast.type.primitiveType.BooleanType;
import main.ast.type.primitiveType.IntType;
import main.ast.type.primitiveType.StringType;
import main.symbolTable.*;
import main.symbolTable.itemException.ItemNotFoundException;
import main.symbolTable.symbolTableVariableItem.SymbolTableVariableItem;
import main.visitor.VisitorImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class TypeChecker extends VisitorImpl {

    public ArrayList<String> nameErrors;

    public TypeChecker() {
        nameErrors = new ArrayList<>();
    }

    public int numOfErrors() {
        return nameErrors.size();
    }

    public void addError(int line, String msg) {
        String s = String.format("Line:%d:%s", line, msg);
        System.out.println(s);
        nameErrors.add(s);
    }

    private boolean hasValidType(String s) {
        if (s.equals("int") || s.equals("int[]") || s.equals("string") || s.equals("boolean") || s.equals("noType"))
            return true;
        return false;
    }

    private String isSelfSenderOrIdentifier;
    private ActorDeclaration actorWeAreIn;
    private int areWeInFor;
    private boolean inInitHandler;
    private ArrayList<ActorDeclaration> actorDecs;

    private String getActorClassName(String actorname) {
        for (VarDeclaration tmp : actorWeAreIn.getKnownActors())
            if (tmp.getIdentifier().getName().equals(actorname))
                return tmp.getType().toString();
        return "";
    }

    private void printTopSymbolTable() {
        System.out.println(Arrays.asList(SymbolTable.top.getSymbolTableItems()));
    }

    protected void visitStatement(Statement stat) {
        if (stat == null)
            return;
        else if (stat instanceof MsgHandlerCall)
            this.visit((MsgHandlerCall) stat);
        else if (stat instanceof Block)
            this.visit((Block) stat);
        else if (stat instanceof Conditional)
            this.visit((Conditional) stat);
        else if (stat instanceof For)
            this.visit((For) stat);
        else if (stat instanceof Break)
            this.visit((Break) stat);
        else if (stat instanceof Continue)
            this.visit((Continue) stat);
        else if (stat instanceof Print)
            this.visit((Print) stat);
        else if (stat instanceof Assign)
            this.visit((Assign) stat);
    }

    protected void visitExpr(Expression expr) {
        if (expr == null)
            return;
        else if (expr instanceof UnaryExpression)
            this.visit((UnaryExpression) expr);
        else if (expr instanceof BinaryExpression)
            this.visit((BinaryExpression) expr);
        else if (expr instanceof ArrayCall)
            this.visit((ArrayCall) expr);
        else if (expr instanceof ActorVarAccess)
            this.visit((ActorVarAccess) expr);
        else if (expr instanceof Identifier)
            this.visit((Identifier) expr);
        else if (expr instanceof Self)
            this.visit((Self) expr);
        else if (expr instanceof Sender)
            this.visit((Sender) expr);
        else if (expr instanceof BooleanValue)
            this.visit((BooleanValue) expr);
        else if (expr instanceof IntValue)
            this.visit((IntValue) expr);
        else if (expr instanceof StringValue)
            this.visit((StringValue) expr);
    }

    @Override
    public void visit(Program program) {
        actorDecs = program.getActors();
        for (ActorDeclaration actordec : actorDecs)
            visit(actordec);
        visit(program.getMain());
    }

    @Override
    public void visit(ActorDeclaration actorDeclaration) {

        //presettings
        try {
            actorWeAreIn = actorDeclaration;
            SymbolTable.top = ((SymbolTableActorItem) SymbolTable.root.get(SymbolTableActorItem.STARTKEY + actorDeclaration.getName().getName())).getActorSymbolTable();

        } catch (ItemNotFoundException exp) {
            addError(actorDeclaration.getName().getLine(), "ERROR CODE #0ff");
        }

        //test if EXTEND is right
        if (actorDeclaration.getParentName() != null) {
            try {
                ((SymbolTableActorItem) SymbolTable.root.get(SymbolTableActorItem.STARTKEY + actorDeclaration.getParentName().getName())).getActorSymbolTable();
            } catch (ItemNotFoundException exp) {
                addError(actorDeclaration.getName().getLine(), String.format("actor %s is not declared", actorDeclaration.getParentName().getName()));
            }
        }

        //know actors
        for (VarDeclaration tmp : actorDeclaration.getKnownActors()) {
            try {
                SymbolTable.root.get(SymbolTableActorItem.STARTKEY + tmp.getType().toString());
            } catch (ItemNotFoundException exp) {
                addError(tmp.getIdentifier().getLine(), String.format("actor %s is not declared", tmp.getType().toString()));
                try {
                    SymbolTableVariableItem test = (SymbolTableVariableItem) SymbolTable.top.get(SymbolTableVariableItem.STARTKEY + tmp.getIdentifier().getName());
                    test.setType(new NoType());
                } catch (ItemNotFoundException expP) {
                    addError(tmp.getIdentifier().getLine(), "ERROR CODE #2ff");
                }
            }
        }

        //actorvars
        for (VarDeclaration tmp : actorDeclaration.getActorVars()) {
            if (hasValidType(tmp.getType().toString()) == false)
                addError(tmp.getIdentifier().getLine(), "actor var must be int, string, boolean, or int[]");
        }

        inInitHandler = true;
        if (actorDeclaration.getInitHandler() != null)
            visit(actorDeclaration.getInitHandler());
        inInitHandler = false;

        //msgHandler visit
        for (MsgHandlerDeclaration tmp : actorDeclaration.getMsgHandlers())
            visit(tmp);

        actorWeAreIn = null;
    }

    @Override
    public void visit(HandlerDeclaration handlerDeclaration) {
        try {
            SymbolTable.top = ((SymbolTableHandlerItem) SymbolTable.top.get(SymbolTableHandlerItem.STARTKEY + handlerDeclaration.getName().getName())).getHandlerSymbolTable();
        } catch (ItemNotFoundException exp) {
            addError(handlerDeclaration.getLine(), "CODE #1FF");
        }
        for (Statement tmp : handlerDeclaration.getBody()) {
            visitStatement(tmp);
        }
        SymbolTable.top = SymbolTable.top.getPreSymbolTable();
    }

    @Override
    public void visit(VarDeclaration varDeclaration) {
        //TODO WHAT TO ADD ?
    }

    @Override
    public void visit(Main mainActors) {
        //TODO: implement appropriate visit functionality

        try {
            SymbolTable.top = ((SymbolTableMainItem) SymbolTable.root.get("Main_main")).getMainSymbolTable();
            for (ActorInstantiation tmp : mainActors.getMainActors())
                visit(tmp);
        } catch (ItemNotFoundException exp) {
            addError(mainActors.getLine(), "main symbol table not found error code #4ff");
        }
    }

    @Override
    public void visit(ActorInstantiation actorInstantiation) {
        ActorDeclaration thisActor = getActorDeclaration(actorInstantiation.getType().toString());
        ArrayList<VarDeclaration> allKnownActors = new ArrayList<>();
        if (thisActor == null) {
//            TODO: fix this error msg
            addError(actorInstantiation.getLine(), "actor type not declared");
            return;
        }

        //if size of known actors are the same as passed

        //WE SHOULD ALSO GET ITS PARENTS KNOWNACTORS
        ActorDeclaration par = thisActor;
        while (par != null) {
            for (VarDeclaration knownactor : par.getKnownActors())
                allKnownActors.add(knownactor);

            if (!par.hasParent)
                break;
            par = getActorDeclaration(par.getParentName().getName());
        }
        if (allKnownActors.size() != actorInstantiation.getKnownActors().size()) {
            addError(actorInstantiation.getLine(), "knownactors does not match with definition");
            return;
        }

        for (int i = 0; i < actorInstantiation.getKnownActors().size(); i++) {
            Identifier id = actorInstantiation.getKnownActors().get(i);
            visit(id);
            String first = id.getType().toString();
            String second = allKnownActors.get(i).getType().toString();
            if (first.equals("noType") || second.equals("noType"))
                return;
            if (!first.equals(second)) {
                addError(actorInstantiation.getLine(), "knownactors does not match with definition");
                return;
            }
        }

        //intial handler call check
        if (thisActor.getInitHandler() == null) {
            if (actorInstantiation.getInitArgs().size() != 0)
                addError(actorInstantiation.getLine(), "initial vars does not match with definition");
            return;
        }

        if (actorInstantiation.getInitArgs().size() != thisActor.getInitHandler().getArgs().size()) {
            addError(actorInstantiation.getLine(), "initial vars does not match with definition");
            return;

        }
        for (int i = 0; i < actorInstantiation.getInitArgs().size(); i++) {
            Expression id = actorInstantiation.getInitArgs().get(i);
            visitExpr(id);
            String first = id.getType().toString();
            String second = thisActor.getInitHandler().getArgs().get(i).getType().toString();
            if (first.equals("noType") || second.equals("noType"))
                continue;
            if (!first.equals(second)) {
                addError(actorInstantiation.getLine(), "initial vars does not match with definition");
                return;
            }
        }

    }

    private ActorDeclaration getActorDeclaration(String s) {
        for (ActorDeclaration actordec : actorDecs) {
            if (actordec.getName().getName().equals(s))
                return actordec;
        }
        return null;
    }


    @Override
    public void visit(UnaryExpression unaryExpression) {
        visitExpr(unaryExpression.getOperand());
        if (unaryExpression.getUnaryOperator() == UnaryOperator.not) {
            if (unaryExpression.getOperand().getType().toString() == "boolean" ||
                    unaryExpression.getOperand().getType().toString() == "noType") {
                unaryExpression.setType(unaryExpression.getOperand().getType());
            } else {
                addError(unaryExpression.getLine(), "not a boolean type to use operator not");
                unaryExpression.setType(new NoType());
            }
            return;
        }
        boolean isTrue = false;
        if (unaryExpression.getOperand() instanceof Identifier) {
            Identifier exp = (Identifier) unaryExpression.getOperand();
            if (exp.getType().toString().equals("int") || exp.getType().toString().equals("int[]") || exp.getType().toString().equals("noType"))
                isTrue = true;
        } else if (unaryExpression.getOperand() instanceof ArrayCall)
            isTrue = true;

        if (isTrue)
            unaryExpression.setType(unaryExpression.getOperand().getType());
        else {
            unaryExpression.setType(new NoType());
            addError(unaryExpression.getLine(), "lvalue required as increment/decrement operand");
        }
    }

    @Override
    public void visit(BinaryExpression binaryExpression) {
        Expression l = binaryExpression.getLeft();
        Expression r = binaryExpression.getRight();
        visitExpr(l);
        visitExpr(r);

        if (binaryExpression.getBinaryOperator() == BinaryOperator.add ||
                binaryExpression.getBinaryOperator() == BinaryOperator.mult ||
                binaryExpression.getBinaryOperator() == BinaryOperator.sub ||
                binaryExpression.getBinaryOperator() == BinaryOperator.div ||
                binaryExpression.getBinaryOperator() == BinaryOperator.mod
        ) {
            if (!(l.getType().toString().equals("int")) && !(l.getType().toString().equals("noType"))) {
                addError(l.getLine(), String.format("unsupported operand type for %s", binaryExpression.getBinaryOperator()));
                binaryExpression.setType(new NoType());
            } else if (!(r.getType().toString().equals("int")) && !(r.getType().toString().equals("noType"))) {
                addError(r.getLine(), String.format("unsupported operand type for %s", binaryExpression.getBinaryOperator()));
                binaryExpression.setType(new NoType());
            } else if (r.getType().toString().equals("noType") || l.getType().toString().equals("noType")) {
                binaryExpression.setType(new NoType());
            } else {
                binaryExpression.setType(l.getType());
            }
        }

        if (binaryExpression.getBinaryOperator() == BinaryOperator.gt ||
                binaryExpression.getBinaryOperator() == BinaryOperator.lt
        ) {

            if (!(l.getType().toString().equals("int")) && !(l.getType().toString().equals("noType"))) {
                addError(l.getLine(), String.format("unsupported operand type for %s", binaryExpression.getBinaryOperator()));
                binaryExpression.setType(new NoType());
            } else if (!(r.getType().toString().equals("int")) && !(r.getType().toString().equals("noType"))) {
                addError(r.getLine(), String.format("unsupported operand type for %s", binaryExpression.getBinaryOperator()));
                binaryExpression.setType(new NoType());
            } else if (r.getType().toString().equals("noType") || l.getType().toString().equals("noType")) {
                binaryExpression.setType(new NoType());
            } else {
                binaryExpression.setType(new BooleanType());
            }
        }


        if (binaryExpression.getBinaryOperator() == BinaryOperator.or ||
                binaryExpression.getBinaryOperator() == BinaryOperator.and
        ) {
            if (!(l.getType().toString().equals("boolean")) && !(l.getType().toString().equals("noType"))) {
                addError(l.getLine(), String.format("unsupported operand type for %s", binaryExpression.getBinaryOperator()));
                binaryExpression.setType(new NoType());
            } else if (!(r.getType().toString().equals("boolean")) && !(r.getType().toString().equals("noType"))) {
                addError(r.getLine(), String.format("unsupported operand type for %s", binaryExpression.getBinaryOperator()));
                binaryExpression.setType(new NoType());
            } else if (r.getType().toString().equals("noType") || l.getType().toString().equals("noType"))
                binaryExpression.setType(new NoType());
            else
                binaryExpression.setType(l.getType());
        }

        if (binaryExpression.getBinaryOperator() == BinaryOperator.eq ||
                binaryExpression.getBinaryOperator() == BinaryOperator.neq
        ) {
            if (l.getType().toString().equals("noType") || r.getType().toString().equals("noType")) {
                binaryExpression.setType(new NoType());
            } else if (!l.getType().toString().equals(r.getType().toString())) {
                addError(l.getLine(), String.format("unsupported operand type for %s", binaryExpression.getBinaryOperator()));
                binaryExpression.setType(new NoType());
            } else if (l.getType().toString().equals("int[]") || r.getType().toString().equals("int[]")) {
                binaryExpression.setType(new NoType());
                addError(l.getLine(), String.format("unsupported operand type for %s", binaryExpression.getBinaryOperator()));
            } else {
                binaryExpression.setType(l.getType());
            }
        }

        if (binaryExpression.getBinaryOperator() == BinaryOperator.assign
        ) {
            if (!(l instanceof Identifier)) {
                binaryExpression.setType(new NoType());
                addError(l.getLine(), "left side of assignment must be a valid lvalue");
            } else if (l.getType().toString().equals("noType") || r.getType().toString().equals("noType")) {
                binaryExpression.setType(new NoType());
            } else if (!l.getType().toString().equals(r.getType().toString())) {
                addError(l.getLine(), "left side of assignment must have same type of right side");
                binaryExpression.setType(new NoType());
            } else if (l.getType().toString().equals("int[]") || r.getType().toString().equals("int[]")) {
                binaryExpression.setType(new NoType());
                addError(l.getLine(), String.format("unsupported operand type for %s", binaryExpression.getBinaryOperator()));
            } else {
                binaryExpression.setType(l.getType());
            }
        }

    }

    @Override
    public void visit(ArrayCall arrayCall) {
        visitExpr(arrayCall.getArrayInstance());
        if (!arrayCall.getArrayInstance().getType().toString().equals("int[]") && !arrayCall.getArrayInstance().getType().toString().equals("noType")) {
            addError(arrayCall.getLine(), String.format("variable %s is not an array", ((Identifier) arrayCall.getArrayInstance()).getName()));
            arrayCall.setType(new NoType());
        }

        visitExpr(arrayCall.getIndex());
        if (arrayCall.getIndex().getType().toString() != "int" && arrayCall.getIndex().getType().toString() != "noType") {
            addError(arrayCall.getLine(), "integer value must be provided between [] of an array");
            arrayCall.setType(new NoType());

        }

        if (arrayCall.getType() == null)
            arrayCall.setType(new IntType());
    }

    @Override
    public void visit(ActorVarAccess actorVarAccess) {
        //TODO: implement appropriate visit functionality
        visitExpr(actorVarAccess.getVariable());
        actorVarAccess.setType(actorVarAccess.getVariable().getType());

    }

    @Override
    public void visit(Identifier identifier) {
        isSelfSenderOrIdentifier = "identifier";
        String searchVal = SymbolTableVariableItem.STARTKEY + identifier.getName();
        try {
            SymbolTableVariableItem getItem = (SymbolTableVariableItem) SymbolTable.top.get(searchVal);
            identifier.setType(getItem.getType());
        } catch (ItemNotFoundException exp) {
//            if (identifier.getType() != null && identifier.getType().toString().equals("noType"))
//                return;
            addError(identifier.getLine(), String.format("variable %s is not declared", identifier.getName()));
            identifier.setType(new NoType());
        }
    }

    @Override
    public void visit(Self self) {
        isSelfSenderOrIdentifier = "self";
        if (actorWeAreIn == null) {
            addError(self.getLine(), "self should be called inside an actor declaration");
        }
        self.setType(actorWeAreIn.getName().getType());
    }

    @Override
    public void visit(Sender sender) {
        isSelfSenderOrIdentifier = "sender";
        if (inInitHandler) {
            addError(sender.getLine(), "no sender in initial msghandler");
        }
        if (actorWeAreIn == null) {
            addError(sender.getLine(), "sender should be called inside an actor declaration");
        }
//        sender.setType(new NoType());
    }

    @Override
    public void visit(BooleanValue value) {
        value.setType(new BooleanType());
    }

    @Override
    public void visit(IntValue value) {
        value.setType(new IntType());
    }

    @Override
    public void visit(StringValue value) {
        value.setType(new StringType());
    }

    @Override
    public void visit(Block block) {
        for (Statement tmp : block.getStatements())
            visitStatement(tmp);
    }

    @Override
    public void visit(Conditional conditional) {
        //TODO: implement appropriate visit functionality
        visitExpr(conditional.getExpression());
        visitStatement(conditional.getThenBody());
        //if it has else
        if (conditional.getElseBody() != null)
            visitStatement(conditional.getElseBody());

        if (conditional.getExpression().getType().toString().equals("boolean") == false)
            addError(conditional.getExpression().getLine(), "condition type must be Boolean");
    }

    @Override
    public void visit(For loop) {
        areWeInFor++;
        visitStatement(loop.getInitialize());
        visitExpr(loop.getCondition());
        if (loop.getCondition() == null) {
            addError(loop.getCondition().getLine(), "no condition declared");
        } else if (loop.getCondition().getType().toString().equals("boolean") == false)
            addError(loop.getCondition().getLine(), "condition type must be Boolean");
        visitStatement(loop.getUpdate());
        areWeInFor--;
    }

    @Override
    public void visit(Break breakLoop) {
        if (areWeInFor == 0)
            addError(breakLoop.getLine(), "break statement not within loop");
        if (areWeInFor < 0)
            addError(breakLoop.getLine(), "error code #3ff");
    }

    @Override
    public void visit(Continue continueLoop) {
        if (areWeInFor == 0)
            addError(continueLoop.getLine(), "continue statement not within loop");
        if (areWeInFor < 0)
            addError(continueLoop.getLine(), "error code #3ff");
    }

    @Override
    public void visit(MsgHandlerCall msgHandlerCall) {
        //TODO: implement appropriate visit functionality
        //check if there is such msgHandler
        //avval bayad check konim ke bara kiio call karde ... self bude ya sender bude ya yek kase dg
        msgHandlerCall.getInstance().setLine(msgHandlerCall.getLine());
        visitExpr(msgHandlerCall.getInstance());
        //551 BUG their bug :|
        if (msgHandlerCall.getInstance() instanceof Identifier) {
            Identifier id = (Identifier) msgHandlerCall.getInstance();
            String whatType = getActorClassName(id.getName());
            if (id.getType().toString() == "noType") return;
            if (whatType.equals("")) {
                addError(id.getLine(), String.format("variable %s is not callable", id.getName()));
            } else {
                try {
                    SymbolTableHandlerItem tmp = (SymbolTableHandlerItem) (((SymbolTableActorItem) SymbolTable.root.get(SymbolTableActorItem.STARTKEY + whatType)).getActorSymbolTable().get(SymbolTableHandlerItem.STARTKEY + msgHandlerCall.getMsgHandlerName().getName()));
                    // hala bayad validity field hayy ke pass mide ro barresi konim
                    ActorDeclaration thisActor = getActorDeclaration(whatType);
                    MsgHandlerDeclaration thisMsgHandler = (MsgHandlerDeclaration) tmp.getHandlerDeclaration();
                    if (msgHandlerCall.getArgs().size() != thisMsgHandler.getArgs().size()) {
                        addError(msgHandlerCall.getLine(), "arguments do not match with definition");
                        return;
                    }
                    for (int i = 0; i < msgHandlerCall.getArgs().size(); i++) {
                        Expression msgHandlerArg = msgHandlerCall.getArgs().get(i);
                        VarDeclaration varDec = thisMsgHandler.getArgs().get(i);
                        visitExpr(msgHandlerArg);
                        visit(varDec);

                        String first = msgHandlerArg.getType().toString();
                        String second = varDec.getType().toString();

                        if (first.equals("noType") || second.equals("noType"))
                            continue;
                        if (!first.equals(second)) {
                            addError(msgHandlerArg.getLine(), "arguments do not match with definition");
                            return;
                        }
                    }
                } catch (ItemNotFoundException exp) {
                    addError(id.getLine(), String.format("there is no msghandler name %s in actor %s", msgHandlerCall.getMsgHandlerName().getName(), id.getName()));
                }
            }
        } else if (msgHandlerCall.getInstance() instanceof Self) {
            Self id = (Self) msgHandlerCall.getInstance();
            String s = msgHandlerCall.getMsgHandlerName().getName();
            try {
                SymbolTable.top.get(SymbolTableHandlerItem.STARTKEY + s);
            } catch (ItemNotFoundException exp) {
                addError(msgHandlerCall.getLine(), String.format("there is no msghandler name %s in this actor", msgHandlerCall.getMsgHandlerName().getName()));
            }
        } else if (msgHandlerCall.getInstance() instanceof Sender) {
            Sender id = (Sender) msgHandlerCall.getInstance();
            //DO nothing
        }
    }

    private MsgHandlerDeclaration findMsgHandlerDec(ActorDeclaration thisActor, String name) {
        ArrayList<MsgHandlerDeclaration> msgHandlers = thisActor.getMsgHandlers();
        for (MsgHandlerDeclaration msg : msgHandlers) {
            if (msg.getName().getName().equals(name))
                return msg;
        }
        return null;
    }

    @Override
    public void visit(Print print) {
        visitExpr(print.getArg());
        if (print.getArg().getType() == null || !hasValidType(print.getArg().getType().toString())) {
            addError(print.getLine(), "unsupported type for print");
        }
    }

    @Override
    public void visit(Assign assign) {
        //TODO: implement appropriate visit functionality
        Expression l = assign.getlValue();
        Expression r = assign.getrValue();

        visitExpr(l);
        visitExpr(r);

        if (!(l instanceof Identifier)) {
            addError(l.getLine(), "left side of assignment must be a valid lvalue");
        }

        if (l.getType() == null || l.getType() instanceof NoType) {
            return;
        }
        if (r.getType() == null || r.getType() instanceof NoType) {
            return;
        }
        if (!canNotBeAssignedTo(l.getType().toString(), r.getType().toString())) {
            addError(l.getLine(), "left side of assignment must have same type of right side or be a subtype");
        } else if (l.getType().toString().equals("int[]") || r.getType().toString().equals("int[]")) {
            addError(l.getLine(), "arrays can not be assigned");
        }
    }

    private boolean canNotBeAssignedTo(String l, String r) {
        if (l.equals(r)) return true;
        ActorDeclaration actR = getActorDeclaration(r);
        while (actR != null) {
            if (actR.getName().getName().equals(l))
                return true;
            if (actR.getParentName() == null)
                break;
            actR = getActorDeclaration(actR.getParentName().getName());
        }
        return false;
    }
}
