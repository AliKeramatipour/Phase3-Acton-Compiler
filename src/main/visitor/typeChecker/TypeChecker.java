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
import main.ast.type.actorType.ActorType;
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

public class TypeChecker extends VisitorImpl {

    private ArrayList<String> nameErrors;

    public TypeChecker() {
        nameErrors = new ArrayList<>();
    }

    public int numOfErrors() {
        return nameErrors.size();
    }

    private void addError(int line, String msg) {
        String s = String.format("Line:%d:%s", line, msg);
        System.out.println(s);
        nameErrors.add(s);
    }

    private boolean hasValidType(Type type) {
        return type instanceof IntType
                || type instanceof ArrayType
                || type instanceof StringType
                || type instanceof BooleanType
                || type instanceof NoType;
    }

    private ActorDeclaration actorWeAreIn;
    private int areWeInFor;
    private boolean inInitHandler;
    private ArrayList<ActorDeclaration> actorDecs;

    private String getActorClassName(String actorName) {
        for (VarDeclaration tmp : actorWeAreIn.getKnownActors())
            if (tmp.getIdentifier().getName().equals(actorName))
                return tmp.getType().toString();
        return "";
    }

    @Override
    public void visit(Program program) {
        actorDecs = program.getActors();
        for (ActorDeclaration actorDec : actorDecs)
            visit(actorDec);
        visit(program.getMain());
    }

    @Override
    public void visit(ActorDeclaration actorDeclaration) {

        try {
            actorWeAreIn = actorDeclaration;
            SymbolTable.top = ((SymbolTableActorItem) SymbolTable.root.get(SymbolTableActorItem.STARTKEY + actorDeclaration.getName().getName())).getActorSymbolTable();

        } catch (ItemNotFoundException exp) {
            addError(actorDeclaration.getName().getLine(), "ERROR CODE #0ff");
        }

        if (actorDeclaration.getParentName() != null) {
            try {
                ((SymbolTableActorItem) SymbolTable.root.get(SymbolTableActorItem.STARTKEY + actorDeclaration.getParentName().getName())).getActorSymbolTable();
            } catch (ItemNotFoundException exp) {
                addError(actorDeclaration.getName().getLine(), String.format("actor %s is not declared", actorDeclaration.getParentName().getName()));
            }
        }

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

        for (VarDeclaration tmp : actorDeclaration.getActorVars()) {
            if (!hasValidType(tmp.getType()))
                addError(tmp.getIdentifier().getLine(), "actor var must be int, string, boolean, or int[]");
        }

        inInitHandler = true;
        if (actorDeclaration.getInitHandler() != null)
            visit(actorDeclaration.getInitHandler());
        inInitHandler = false;

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
        if (thisActor == null) {
            addError(actorInstantiation.getLine(), String.format("actor %s is not declared", actorInstantiation.getType()));
            return;
        }

        ArrayList<VarDeclaration> allKnownActors = new ArrayList<>();
        ActorDeclaration curr = thisActor;
        while (curr != null) {
            allKnownActors.addAll(curr.getKnownActors());
            if (!curr.hasParent)
                break;
            curr = getActorDeclaration(curr.getParentName().getName());
        }

        boolean hasKnownActorMatchError = false;
        ArrayList<Identifier> knownActors = actorInstantiation.getKnownActors();
        if (allKnownActors.size() != knownActors.size()) {
            hasKnownActorMatchError = true;
        }

        for (Identifier id : knownActors) {
            visit(id);
        }
        int minSize = Math.min(knownActors.size(), allKnownActors.size());
        for (int i = 0; i < minSize; i++) {
            Identifier id = knownActors.get(i);

            Type first = id.getType();
            Type second = allKnownActors.get(i).getType();
            if (first instanceof NoType || second instanceof NoType)
                continue;
            if (!first.toString().equals(second.toString())) {
                hasKnownActorMatchError = true;
            }
        }
        if (hasKnownActorMatchError) {
            addError(actorInstantiation.getLine(), "knownactors do not match with definition");
        }


        boolean hasInitArgMatchError = false;
        ArrayList<Expression> initArgs = actorInstantiation.getInitArgs();
        for (Expression exp : initArgs) {
            visitExpr(exp);
        }

        InitHandlerDeclaration initHandlerDeclaration = thisActor.getInitHandler();
        if (initHandlerDeclaration == null) {
            if (actorInstantiation.getInitArgs().size() != 0)
                hasInitArgMatchError = true;
        } else {
            ArrayList<VarDeclaration> initHandlerArgs = initHandlerDeclaration.getArgs();
            if (initArgs.size() != initHandlerArgs.size()) {
                hasInitArgMatchError = true;
            }

            minSize = Math.min(initArgs.size(), initHandlerArgs.size());
            for (int i = 0; i < minSize; i++) {
                Expression exp = initArgs.get(i);

                Type first = exp.getType();
                Type second = initHandlerArgs.get(i).getType();
                if (first instanceof NoType || second instanceof NoType)
                    continue;
                if (!first.toString().equals(second.toString())) {
                    hasInitArgMatchError = true;

                }
            }
        }
        if (hasInitArgMatchError) {
            addError(actorInstantiation.getLine(), "arguments do not match with definition");
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
        Expression unaryExpressionOperand = unaryExpression.getOperand();
        visitExpr(unaryExpressionOperand);

        UnaryOperator unaryOperator = unaryExpression.getUnaryOperator();
        Type unaryExpressionOperandType = unaryExpressionOperand.getType();
        if (unaryExpressionOperandType == null) {
            unaryExpression.setType(new NoType());
            return;
        }

        if (unaryOperator == UnaryOperator.not) {
            if (unaryExpressionOperandType instanceof BooleanType || unaryExpressionOperandType instanceof NoType) {
                unaryExpression.setType(unaryExpressionOperandType);
            } else {
                addError(unaryExpression.getLine(), String.format("unsupported operand type for %s", unaryOperator));
                unaryExpression.setType(new NoType());
            }
        } else if (unaryOperator == UnaryOperator.minus) {
        } else {
            String unaryOperatorType = "";
            if (unaryOperator == UnaryOperator.preinc || unaryOperator == UnaryOperator.postinc) {
                unaryOperatorType = "increment";
            } else if (unaryOperator == UnaryOperator.predec || unaryOperator == UnaryOperator.postdec) {
                unaryOperatorType = "decrement";
            }

            boolean isOk = unaryExpressionOperandType instanceof IntType;

            if (isOk)
                unaryExpression.setType(unaryExpressionOperandType);
            else {
                unaryExpression.setType(new NoType());
                addError(unaryExpression.getLine(), String.format("unsupported operand type for %s", unaryOperatorType));
            }

            if (!isLeftValue(unaryExpressionOperand)) {
                addError(unaryExpression.getLine(), String.format("lvalue required as %s operand", unaryOperatorType));
            }

        }
    }

    @Override
    public void visit(BinaryExpression binaryExpression) {
        Expression l = binaryExpression.getLeft();
        Expression r = binaryExpression.getRight();
        visitExpr(l);
        visitExpr(r);

        BinaryOperator binaryOperator = binaryExpression.getBinaryOperator();
        Type lType = l.getType();
        Type rType = r.getType();

        if (binaryOperator == BinaryOperator.add ||
                binaryOperator == BinaryOperator.mult ||
                binaryOperator == BinaryOperator.sub ||
                binaryOperator == BinaryOperator.div ||
                binaryOperator == BinaryOperator.mod
        ) {
            boolean isLTypeOk = lType instanceof IntType;
            boolean isRTypeOk = rType instanceof IntType;
            boolean isOk = isLTypeOk && isRTypeOk;

            if (isOk) {
                binaryExpression.setType(l.getType());
            } else {
                binaryExpression.setType(new NoType());

                if (!isLTypeOk) {
                    addError(l.getLine(), String.format("unsupported operand type for %s", binaryOperator));
                } else {
                    addError(r.getLine(), String.format("unsupported operand type for %s", binaryOperator));
                }
            }
        }

        if (binaryOperator == BinaryOperator.gt ||
                binaryOperator == BinaryOperator.lt
        ) {
            binaryExpression.setType(new BooleanType());

            boolean isLTypeOk = lType instanceof IntType;
            boolean isRTypeOk = rType instanceof IntType;

            if (!isLTypeOk) {
                addError(l.getLine(), String.format("unsupported operand type for %s", binaryOperator));
            } else if (!isRTypeOk) {
                addError(r.getLine(), String.format("unsupported operand type for %s", binaryOperator));
            }

        }


        if (binaryOperator == BinaryOperator.or ||
                binaryOperator == BinaryOperator.and
        ) {
            binaryExpression.setType(new BooleanType());

            boolean isLTypeOk = lType instanceof BooleanType;
            boolean isRTypeOk = rType instanceof BooleanType;

            if (!isLTypeOk) {
                addError(l.getLine(), String.format("unsupported operand type for %s", binaryOperator));
            } else if (!isRTypeOk) {
                addError(r.getLine(), String.format("unsupported operand type for %s", binaryOperator));
            }
        }

        boolean isOk = lType.toString().equals(rType.toString()) && !(lType instanceof NoType || rType instanceof NoType);
        if (binaryOperator == BinaryOperator.eq ||
                binaryOperator == BinaryOperator.neq
        ) {
            binaryExpression.setType(new BooleanType());

            if (lType instanceof ArrayType && rType instanceof ArrayType) {
                isOk = ((ArrayType) lType).getSize() == ((ArrayType) rType).getSize();
            }

            if (!isOk) {
                binaryExpression.setType(new NoType());
                addError(l.getLine(), String.format("unsupported operand type for %s", binaryOperator));
            }
        }

        if (binaryOperator == BinaryOperator.assign) {
            if (isOk) {
                if (lType instanceof ArrayType && rType instanceof ArrayType && ((ArrayType) lType).getSize() != ((ArrayType) rType).getSize()) {
                    addError(l.getLine(), "operation assign requires equal array sizes");
                    binaryExpression.setType(new NoType());
                } else {
                    binaryExpression.setType(l.getType());
                }
            } else {
                binaryExpression.setType(new NoType());
                addError(l.getLine(), String.format("unsupported  operand type for %s", binaryOperator));
            }

            if (!isLeftValue(l)) {
                addError(l.getLine(), "left side of assignment must be a valid lvalue");
            }
        }

    }

    @Override
    public void visit(ArrayCall arrayCall) {
        Expression instance = arrayCall.getArrayInstance();
        visitExpr(instance);
        Type instanceType = instance.getType();
        if (!(instanceType instanceof ArrayType || instanceType instanceof NoType)) {
            addError(arrayCall.getLine(), String.format("variable %s is not an array", ((Identifier) instance).getName()));
            arrayCall.setType(new NoType());
        }

        Expression index = arrayCall.getIndex();
        visitExpr(index);
        Type indexType = index.getType();
        if (!(indexType instanceof IntType || indexType instanceof NoType)) {
            addError(arrayCall.getLine(), "integer value must be provided between [] of an array");
            arrayCall.setType(new NoType());
        }

        if (arrayCall.getType() == null)
            arrayCall.setType(new IntType());
    }

    @Override
    public void visit(ActorVarAccess actorVarAccess) {
        if (actorWeAreIn == null) {
            actorVarAccess.getSelf().accept(this);
        } else {
            Identifier id = actorVarAccess.getVariable();
            visitExpr(id);
            actorVarAccess.setType(id.getType());
        }
    }

    @Override
    public void visit(Identifier identifier) {
        String searchVal = SymbolTableVariableItem.STARTKEY + identifier.getName();
        try {
            SymbolTableVariableItem getItem = (SymbolTableVariableItem) SymbolTable.top.get(searchVal);
            identifier.setType(getItem.getType());
        } catch (ItemNotFoundException exp) {
            addError(identifier.getLine(), String.format("variable %s is not declared", identifier.getName()));
            identifier.setType(new NoType());
        }
    }

    @Override
    public void visit(Self self) {
        if (actorWeAreIn == null) {
            addError(self.getLine(), "self doesn't refer to any actor");
        } else {
            self.setType(actorWeAreIn.getName().getType());
        }
    }

    @Override
    public void visit(Sender sender) {
        if (inInitHandler) {
            addError(sender.getLine(), "no sender in initial msghandler");
        }
        if (actorWeAreIn == null) {
            addError(sender.getLine(), "sender should be called inside an actor declaration");
        }
        sender.setType(new ActorType(new Identifier("SENDER")));
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

    private void checkCondition(Expression e) {
        boolean isOk = e.getType() instanceof BooleanType;
        if (!isOk) {
            addError(e.getLine(), "condition type must be boolean");
        }
    }

    @Override
    public void visit(Conditional conditional) {
        visitExpr(conditional.getExpression());

        checkCondition(conditional.getExpression());

        visitStatement(conditional.getThenBody());

        if (conditional.getElseBody() != null)
            visitStatement(conditional.getElseBody());

    }

    @Override
    public void visit(For loop) {
        areWeInFor++;
        visitStatement(loop.getInitialize());

        Expression condition = loop.getCondition();
        visitExpr(condition);
        checkCondition(condition);

        visitStatement(loop.getUpdate());

        visitStatement(loop.getBody());
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

        Expression instance = msgHandlerCall.getInstance();
        msgHandlerCall.getInstance().setLine(msgHandlerCall.getLine());

        visitExpr(instance);

        String handlerName = msgHandlerCall.getMsgHandlerName().getName();
        if (instance instanceof Identifier) {
            Identifier actorId = (Identifier) instance;
            if (actorId.getType() instanceof NoType)
                return;

            String whatType = getActorClassName(actorId.getName());
            if (whatType.equals("")) {
                addError(actorId.getLine(), String.format("variable %s is not callable", actorId.getName()));
            } else {
                try {
                    SymbolTableHandlerItem tmp = (SymbolTableHandlerItem) (((SymbolTableActorItem) SymbolTable.root.get(SymbolTableActorItem.STARTKEY + whatType)).getActorSymbolTable().get(SymbolTableHandlerItem.STARTKEY + msgHandlerCall.getMsgHandlerName().getName()));
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

                        Type first = msgHandlerArg.getType();
                        Type second = varDec.getType();

                        if (first instanceof NoType || second instanceof NoType)
                            continue;
                        if (!first.toString().equals(second.toString())) {
                            addError(msgHandlerArg.getLine(), "arguments do not match with definition");
                        }
                    }
                } catch (ItemNotFoundException exp) {
                    addError(actorId.getLine(), String.format("there is no msghandler with name %s in actor %s", handlerName, actorId.getName()));
                }
            }
        } else if (instance instanceof Self) {
            try {
                SymbolTable.top.get(SymbolTableHandlerItem.STARTKEY + handlerName);
            } catch (ItemNotFoundException exp) {
                addError(msgHandlerCall.getLine(), String.format("there is no msghandler named %s in actor %s", handlerName, actorWeAreIn.getName().getName()));
            }
        }
    }

    @Override
    public void visit(Print print) {
        Expression arg = print.getArg();
        visitExpr(arg);
        if (!hasValidType(arg.getType())) {
            addError(print.getLine(), "unsupported type for print");
        }
    }

    @Override
    public void visit(Assign assign) {
        Expression l = assign.getlValue();
        Expression r = assign.getrValue();

        visitExpr(l);
        visitExpr(r);

        if (!isLeftValue(l)) {
            addError(l.getLine(), "left side of assignment must be a valid lvalue");
            return;
        }

        Type lType = l.getType();
        Type rType = r.getType();

        if (lType instanceof NoType || rType instanceof NoType) {
            return;
        }

        boolean isOk = canBeAssignedTo(lType, rType);
        if (isOk) {
            if (lType instanceof ArrayType && rType instanceof ArrayType && ((ArrayType) lType).getSize() != ((ArrayType) rType).getSize()) {
                addError(l.getLine(), "operation assign requires equal array sizes");
            }
        } else {
            addError(l.getLine(), "unsupported operand type for assign");
        }
    }

    private boolean canBeAssignedTo(Type lType, Type rType) {
        if (lType.toString().equals(rType.toString()))
            return true;
        ActorDeclaration actR = getActorDeclaration(rType.toString());
        while (actR != null) {
            if (actR.getName().getName().equals(lType.toString()))
                return true;
            if (actR.getParentName() == null)
                break;
            actR = getActorDeclaration(actR.getParentName().getName());
        }
        return false;
    }

    private boolean isLeftValue(Expression e) {
        return e instanceof Identifier || e instanceof ArrayCall || e instanceof ActorVarAccess;
    }
}
