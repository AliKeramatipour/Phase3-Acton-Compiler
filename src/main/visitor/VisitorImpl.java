package main.visitor;
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
import main.symbolTable.*;
import main.symbolTable.itemException.ItemNotFoundException;
import main.symbolTable.symbolTableVariableItem.SymbolTableVariableItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class VisitorImpl implements Visitor {

    public ArrayList<String> nameErrors;

    public VisitorImpl()
    {
        nameErrors = new ArrayList<>();
    }

    public void addError(int line, String msg)
    {
        String s = "Line:" + line +" : " + msg;
        System.out.println(s);
        nameErrors.add(s);
    }

    private boolean hasValidType(String s)
    {
        if ( s.equals("int") || s.equals("int[]") || s.equals("string") || s.equals("boolean") || s.equals("notype") ) return true ;
        return false ;
    }

    private String isSelfSenderOrIdentifier;
    private ActorDeclaration actorWeAreIn ;
    private int areWeInFor;

    private String getActorClassName(String actorname)
    {
        for (VarDeclaration tmp : actorWeAreIn.getKnownActors())
            if ( tmp.getIdentifier().getName().equals(actorname) )
                return tmp.getType().toString();
        return "";
    }

    private void printTopSymbolTable()
    {
        System.out.println(Arrays.asList(SymbolTable.top.getSymbolTableItems()));
    }

    protected void visitStatement( Statement stat )
    {
        if( stat == null )
            return;
        else if( stat instanceof MsgHandlerCall )
            this.visit( ( MsgHandlerCall ) stat );
        else if( stat instanceof Block )
            this.visit( ( Block ) stat );
        else if( stat instanceof Conditional )
            this.visit( ( Conditional ) stat );
        else if( stat instanceof For )
            this.visit( ( For ) stat );
        else if( stat instanceof Break )
            this.visit( ( Break ) stat );
        else if( stat instanceof Continue )
            this.visit( ( Continue ) stat );
        else if( stat instanceof Print )
            this.visit( ( Print ) stat );
        else if( stat instanceof Assign )
            this.visit( ( Assign ) stat );
    }

    protected void visitExpr( Expression expr )
    {
        if( expr == null )
            return;
        else if( expr instanceof UnaryExpression )
            this.visit( ( UnaryExpression ) expr );
        else if( expr instanceof BinaryExpression )
            this.visit( ( BinaryExpression ) expr );
        else if( expr instanceof ArrayCall )
            this.visit( ( ArrayCall ) expr );
        else if( expr instanceof ActorVarAccess )
            this.visit( ( ActorVarAccess ) expr );
        else if( expr instanceof Identifier )
            this.visit( ( Identifier ) expr );
        else if( expr instanceof Self )
            this.visit( ( Self ) expr );
        else if( expr instanceof Sender )
            this.visit( ( Sender ) expr );
        else if( expr instanceof BooleanValue )
            this.visit( ( BooleanValue ) expr );
        else if( expr instanceof IntValue )
            this.visit( ( IntValue ) expr );
        else if( expr instanceof StringValue )
            this.visit( ( StringValue ) expr );
    }

    @Override
    public void visit(Program program) {
        //TODO: implement appropriate visit functionality
        ArrayList<ActorDeclaration> actors = program.getActors();
        for ( int i = 0 ; i < actors.size() ; i++ )
        {
            ActorDeclaration actordec = actors.get(i);
            visit(actordec);
        }
    }

    @Override
    public void visit(ActorDeclaration actorDeclaration) {

        System.out.println("actor in  :" + actorDeclaration.getName().getName());
        //presettings
        try {
            actorWeAreIn = actorDeclaration;
            SymbolTable.top = ((SymbolTableActorItem) SymbolTable.root.get("Actor_" + actorDeclaration.getName().getName())).getActorSymbolTable();
        }catch ( ItemNotFoundException exp)
        {
            addError(actorDeclaration.getName().getLine(), "ERROR CODE #0ff");
        }

        //test if EXTEND is right
        if ( actorDeclaration.getParentName() != null) {
            try {
                ((SymbolTableActorItem) SymbolTable.root.get("Actor_" + actorDeclaration.getParentName().getName())).getActorSymbolTable();
            } catch (ItemNotFoundException exp) {
                addError(actorDeclaration.getName().getLine(), "actor type " + actorDeclaration.getParentName().getName() + " not declared");
            }
        }

        //know actors
        for (VarDeclaration tmp : actorDeclaration.getKnownActors()) {
            try {
                SymbolTable.root.get("Actor_" + tmp.getType().toString());
            } catch (ItemNotFoundException exp) {
                addError(tmp.getIdentifier().getLine(), "actor type " + tmp.getType().toString() + " not declared");
                try {
                    SymbolTableVariableItem test = (SymbolTableVariableItem)SymbolTable.top.get("Variable_" + tmp.getIdentifier().getName());
                    test.setType(new NoType());
                }catch(ItemNotFoundException expP)
                {
                    addError(tmp.getIdentifier().getLine(), "ERROR CODE #2ff");
                }
            }
        }

        //actorvars
        for (VarDeclaration tmp : actorDeclaration.getActorVars()) {
            if (hasValidType(tmp.getType().toString()) == false)
                addError(tmp.getIdentifier().getLine(),"actor var must be int, string, boolean, or int[]" );
        }

        //msgHandler visit
        for (MsgHandlerDeclaration tmp : actorDeclaration.getMsgHandlers() )
            visit(tmp);
        System.out.println("actor out :" + actorDeclaration.getName().getName());
        System.out.println();
    }

    @Override
    public void visit(HandlerDeclaration handlerDeclaration) {
        try {
            SymbolTable.top = ((SymbolTableHandlerItem)SymbolTable.top.get("Handler_" + handlerDeclaration.getName().getName())).getHandlerSymbolTable();
        }catch(ItemNotFoundException exp)
        {
            addError(handlerDeclaration.getLine(), "CODE #1FF");
        }
        for ( Statement tmp : handlerDeclaration.getBody())
            visitStatement(tmp);
        SymbolTable.top = SymbolTable.top.getPreSymbolTable();
    }

    @Override
    public void visit(VarDeclaration varDeclaration) {
        //TODO WHAT TO ADD ?
    }

    @Override
    public void visit(Main mainActors) {
        //TODO: implement appropriate visit functionality
        /*
        try {
            SymbolTable.top = ((SymbolTableMainItem)    SymbolTable.root.get("Main_"    + main))
            SymbolTable.top = ((SymbolTableHandlerItem) SymbolTable.root.get("Handler_" + handlerDeclaration.getName())).getHandlerSymbolTable();
            for ( Statement tmp : handlerDeclaration.getBody())
                visitStatement(tmp);
        } catch (ItemNotFoundException exp)
        {
            System.out.println("visit(handler declaration) : handler symbol table not found");
        }*/

    }

    @Override
    public void visit(ActorInstantiation actorInstantiation) {
        //TODO: implement appropriate visit functionality
    }


    @Override
    public void visit(UnaryExpression unaryExpression) {
        //TODO: implement appropriate visit functionality
        visitExpr(unaryExpression.getOperand());
        if ( unaryExpression.getUnaryOperator() == UnaryOperator.not )
        {
            if ( unaryExpression.getOperand() instanceof Identifier ) {
                if (!unaryExpression.getOperand().getType().toString().equals("boolean")) {
                    addError(unaryExpression.getLine(), "not a boolean type to use operator not");
                    return;
                }
                unaryExpression.setType(unaryExpression.getOperand().getType());
            } else if ( unaryExpression.getOperand() instanceof  BooleanValue)
            {
                unaryExpression.setType(new BooleanType());
                return ;
            }
            addError(unaryExpression.getLine(), "not a boolean type to use operator not");
            return ;
        }

        boolean isTrue = false ;
        if ( unaryExpression.getOperand() instanceof  Identifier )
        {
            Identifier exp = (Identifier) unaryExpression.getOperand();
            if ( exp.getType().toString().equals("int") || exp.getType().toString().equals("int[]") || exp.getType().toString().equals("notype") )
                isTrue = true;
        } else if ( unaryExpression.getOperand() instanceof  ArrayCall )
            isTrue = true;

        if ( isTrue )
            unaryExpression.setType(unaryExpression.getOperand().getType());
        else
            addError(unaryExpression.getLine(), "lvalue required as increment/decrement operand");
    }

    @Override
    public void visit(BinaryExpression binaryExpression) {
        //TODO: implement appropriate visit functionality
        Expression l = binaryExpression.getLeft();

        Expression r = binaryExpression.getRight();
        visitExpr(l);
        visitExpr(r);

        if (    binaryExpression.getBinaryOperator() == BinaryOperator.add ||
                binaryExpression.getBinaryOperator() == BinaryOperator.mult ||
                binaryExpression.getBinaryOperator() == BinaryOperator.sub ||
                binaryExpression.getBinaryOperator() == BinaryOperator.div ||
                binaryExpression.getBinaryOperator() == BinaryOperator.mod ||
                binaryExpression.getBinaryOperator() == BinaryOperator.gt  ||
                binaryExpression.getBinaryOperator() == BinaryOperator.lt
            )
        {
            if ( (l.getType().toString() != "int") && (l.getType().toString() != "notype") )
                addError(l.getLine(), "expression needs an integer input type");

            if ( (r.getType().toString() != "int") && (r.getType().toString() != "notype") )
                addError(r.getLine(), "expression needs an integer input type");
        }

        if (    binaryExpression.getBinaryOperator() == BinaryOperator.or ||
                binaryExpression.getBinaryOperator() == BinaryOperator.and
        )
        {
            if ( (l.getType().toString() != "boolean") && (l.getType().toString() != "notype") )
                addError(l.getLine(), "expression needs a boolean input type");

            if ( (r.getType().toString() != "boolean") && (r.getType().toString() != "notype") )
                addError(r.getLine(), "expression needs a boolean input type");
        }

        if (    binaryExpression.getBinaryOperator() == BinaryOperator.eq ||
                binaryExpression.getBinaryOperator() == BinaryOperator.neq
        )
        {
            if ( l.getType().toString().equals("int[]") || r.getType().toString().equals("int[]"))
            {
                binaryExpression.setType(new NoType());
                addError(l.getLine(), "arrays can not be compared");
            } else if ( l.getType().toString() == "notype" || r.getType().toString() == "notype" )
            {
                binaryExpression.setType(new NoType());

            }
        }


        if ( l.getType() == r.getType() )
            binaryExpression.setType(l.getType());
        else if ( l.getType().toString().equals("notype") )
            binaryExpression.setType(l.getType());
        else if ( r.getType().toString().equals("notype") )
            binaryExpression.setType(r.getType());
    }

    @Override
    public void visit(ArrayCall arrayCall) {
        visitExpr(arrayCall.getArrayInstance());
        if (!arrayCall.getArrayInstance().getType().toString().equals("int[]") && !arrayCall.getArrayInstance().getType().toString().equals("notype")) {
            addError(arrayCall.getLine(), "variable " + ((Identifier) arrayCall.getArrayInstance()).getName() + " is not an array");
            arrayCall.setType(new NoType());
        }

        visitExpr(arrayCall.getIndex());
        if (arrayCall.getIndex().getType().toString() != "int" && arrayCall.getIndex().getType().toString() != "notype" ) {
            addError(arrayCall.getLine(), "integer value must be provided between [] of an array");
            arrayCall.setType(new NoType());

        }

        if ( arrayCall.getType() == null )
            arrayCall.setType(new IntType());
    }

    @Override
    public void visit(ActorVarAccess actorVarAccess) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(Identifier identifier) {
        isSelfSenderOrIdentifier = "identifier";
        HashMap tmp = SymbolTable.top.getSymbolTableItems();
        String searchVal = "Variable_" + identifier.getName();
        try{
            SymbolTableVariableItem getItem = (SymbolTableVariableItem) SymbolTable.top.get(searchVal);
            identifier.setType(getItem.getType());
        } catch (ItemNotFoundException exp)
        {
            if (identifier.getType() != null && identifier.getType().toString().equals("notype"))
                return;
            addError(identifier.getLine(), "variable " + identifier.getName() +" not declared");
            identifier.setType(new NoType());
        }
    }

    @Override
    public void visit(Self self) {
        isSelfSenderOrIdentifier = "self";

    }

    @Override
    public void visit(Sender sender) {
        isSelfSenderOrIdentifier = "sender";
    }

    @Override
    public void visit(BooleanValue value) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(IntValue value) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(StringValue value) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(Block block) {
        for (Statement tmp: block.getStatements())
            visitStatement(tmp);
    }

    @Override
    public void visit(Conditional conditional) {
        //TODO: implement appropriate visit functionality
        visitExpr(conditional.getExpression());
        visitStatement(conditional.getThenBody());
        //if it has else
        if ( conditional.getElseBody() != null )
            visitStatement(conditional.getElseBody());

        if ( conditional.getExpression().getType().toString().equals("boolean") == false )
            addError(conditional.getExpression().getLine(), "condition type must be Boolean");
    }

    @Override
    public void visit(For loop) {
        areWeInFor++;
        visitStatement(loop.getInitialize());
        visitExpr(loop.getCondition());
        if ( loop.getCondition() == null )
        {
            addError(loop.getCondition().getLine(), "no condition declared");
        } else if ( loop.getCondition().getType().toString().equals("boolean") == false )
            addError(loop.getCondition().getLine(), "condition type must be Boolean");
        visitStatement(loop.getUpdate());
        areWeInFor--;
    }

    @Override
    public void visit(Break breakLoop) {
        if ( areWeInFor == 0 )
            addError(breakLoop.getLine(), "break statement not within loop" );
        if ( areWeInFor <  0 )
            addError(breakLoop.getLine(), "error code #3ff");
    }

    @Override
    public void visit(Continue continueLoop) {
        if ( areWeInFor == 0 )
            addError(continueLoop.getLine(), "continue statement not within loop" );
        if ( areWeInFor <  0 )
            addError(continueLoop.getLine(), "error code #3ff");
    }

    @Override
    public void visit(MsgHandlerCall msgHandlerCall) {
        //TODO: implement appropriate visit functionality
        //check if there is such msgHandler

        //avval bayad check konim ke bara kiio call karde ... self bude ya sender bude ya yek kase dg
        visitExpr(msgHandlerCall.getInstance());
        if ( isSelfSenderOrIdentifier.equals("identifier") )
        {
            Identifier id = (Identifier)msgHandlerCall.getInstance();
            String whatType = getActorClassName(id.getName());
            if ( id.getType().toString() == "notype") return;
            if ( whatType.equals("") )
            {
                addError(id.getLine(), "variable " + id.getName() + " is not callable");
            }else {
                try {
                    ((SymbolTableActorItem)SymbolTable.root.get("Actor_" + whatType)).getActorSymbolTable().get("Handler_" + msgHandlerCall.getMsgHandlerName().getName());
                } catch (ItemNotFoundException exp) {
                    addError(id.getLine(), "there is no msghandler name " + msgHandlerCall.getMsgHandlerName().getName() + " in actor " + id.getName());
                }
            }
        }
        if ( isSelfSenderOrIdentifier.equals("self") )
        {
            Self id = (Self)msgHandlerCall.getInstance();
            String s = msgHandlerCall.getMsgHandlerName().getName();
            try {
                SymbolTable.top.get("Handler_" + s);
            }catch (ItemNotFoundException exp)
            {
                addError(msgHandlerCall.getLine(), "there is no msghandler name " + msgHandlerCall.getMsgHandlerName().getName() + " in this actor");
            }
        }
        if ( isSelfSenderOrIdentifier.equals("sender") )
        {
            Sender id = (Sender)msgHandlerCall.getInstance();
            //DO nothing
        }
    }

    @Override
    public void visit(Print print) {
        visitExpr(print.getArg());
        if ( !hasValidType(print.getArg().getType().toString()) )
        {
            addError(print.getLine(), "unsupported type for print");
        }
    }

    @Override
    public void visit(Assign assign) {
        //TODO: implement appropriate visit functionality
        Expression Lexp = assign.getlValue();
        Expression Rexp = assign.getrValue();
        visitExpr(Lexp);
        visitExpr(Rexp);
    }

    public int numOfErrors()
    {
        return nameErrors.size();
    }

}
