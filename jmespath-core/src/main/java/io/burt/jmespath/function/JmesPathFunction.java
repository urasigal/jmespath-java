package io.burt.jmespath.function;

import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import io.burt.jmespath.JmesPathRuntime;

/**
 * Base class of all functions.
 * <p>
 * Subclasses must either use the constructor that specifies a name, or be
 * called something that ends with "Function" – in which case the name will
 * automatically be generated from the class name. "MyAwesomeFunction" will
 * get the name "my_awesome", i.e. the camel cased name will be converted to
 * snake case, minus the "Function" suffix.
 * <p>
 * Subclasses must override the {@link #callFunction} method, and not
 * {@link #call}. The latter does type checking on the arguments and then calls
 * {@link #callFunction}.
 * <p>
 * Subclasses must also provide argument constraints for checking arguments.
 * This is done by using the {@link ArgumentConstraints} DSL and passing the
 * result in a <code>super</code> call in the constructor.
 */
public abstract class JmesPathFunction {
  private final ArgumentConstraint argumentConstraints;
  private final String name;

  private static Pattern CAMEL_CASE_COMPONENT_RE = Pattern.compile("[A-Z][^A-Z]+");

  /**
   * Constructor used by subclasses whose name ends with "Function" and that
   * accept a single, or a variable number of arguments.
   *
   * @throws FunctionConfigurationException when the function name cannot be produced from the class name
   */
  public JmesPathFunction(ArgumentConstraint argumentConstraints) {
    this(null, argumentConstraints);
  }

  /**
   * Constructor used by subclasses whose name ends with "Function" and that
   * accept a fixed number of argument.
   *
   * @throws FunctionConfigurationException when the function name cannot be produced from the class name
   */
  public JmesPathFunction(ArgumentConstraint... argumentConstraints) {
    this(null, ArgumentConstraints.listOf(argumentConstraints));
  }

  /**
   * Constructor used by subclasses that provide a custom name (not based on
   * the class name) and that accept a fixed number of arguments.
   */
  public JmesPathFunction(String name, ArgumentConstraint... argumentConstraints) {
    this(name, ArgumentConstraints.listOf(argumentConstraints));
  }

  /**
   * Constructor used by subclasses that provide a custom name (not based on
   * the class name) and that accept a single, or a variable number of arguments.
   */
  public JmesPathFunction(String name, ArgumentConstraint argumentConstraints) {
    this.name = name == null ? classNameToFunctionName() : name;
    this.argumentConstraints = argumentConstraints;
  }

  private String classNameToFunctionName() {
    String n = getClass().getName();
    if (n.indexOf("$") > -1) {
      n = n.substring(n.lastIndexOf("$") + 1);
    } else {
      n = n.substring(n.lastIndexOf(".") + 1);
    }
    if (!n.endsWith("Function")) {
      throw new FunctionConfigurationException(String.format("The function defined by %s must either pass a name to the JmesPathFunction constructor or the class name must end with \"Function\"", getClass().getName()));
    }
    Matcher m = CAMEL_CASE_COMPONENT_RE.matcher(n);
    int offset = 0;
    StringBuilder snakeCaseName = new StringBuilder();
    while (m.find(offset)) {
      String piece = n.substring(m.start(), m.end()).toLowerCase();
      if (piece.equals("function")) {
        break;
      }
      snakeCaseName.append(piece);
      snakeCaseName.append("_");
      offset = m.end();
    }
    snakeCaseName.deleteCharAt(snakeCaseName.length() - 1);
    return snakeCaseName.toString();
  }

  /**
   * Returns the name of the function.
   * <p>
   * The name is either automatically generated from the class name, or
   * explicitly specified in the constructor.
   */
  public String name() {
    return name;
  }

  protected ArgumentConstraint argumentConstraints() {
    return argumentConstraints;
  }

  /**
   * Call this function with a list of arguments.
   *
   * The arguments can be either values or expressions, and will be checked
   * by the function's argument constraints before the function runs.
   */
  public <T> T call(JmesPathRuntime<T> runtime, List<ExpressionOrValue<T>> arguments) {
    checkArguments(runtime, arguments);
    return callFunction(runtime, arguments);
  }

  /**
   * Checks the arguments against the argument constraints.
   *
   * @throws ArgumentTypeException when an arguments type does not match the constraints
   * @throws ArityException when there are too few or too many arguments
   */
  protected <T> void checkArguments(JmesPathRuntime<T> runtime, List<ExpressionOrValue<T>> arguments) {
    try {
      Iterator<ExpressionOrValue<T>> argumentIterator = arguments.iterator();
      argumentConstraints.check(runtime, argumentIterator);
      if (argumentIterator.hasNext()) {
        throw new ArityException(name(), argumentConstraints.minArity(), argumentConstraints.maxArity(), arguments.size());
      }
    } catch (ArgumentConstraints.InternalArityException e) {
      throw new ArityException(name(), argumentConstraints.minArity(), argumentConstraints.maxArity(), arguments.size());
    } catch (ArgumentConstraints.InternalArgumentTypeException e) {
      throw new ArgumentTypeException(name(), e.expectedType(), e.actualType());
    }
  }

  /**
   * Called from {@link #call} after the argument constraints have been checked
   * against the arguments.
   * <p>
   * May perform additional type checking and throw {@link ArgumentTypeException}.
   * For example when using expressions there is currently no way to check the
   * types produced by those expressions without running the function. Functions
   * that accept expressions are responsible for checking the types of the values
   * produced by those expressions.
   */
  protected abstract <T> T callFunction(JmesPathRuntime<T> runtime, List<ExpressionOrValue<T>> arguments);
}
