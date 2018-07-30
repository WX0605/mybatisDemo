package com.torres.mybatis.interceptor;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.scripting.xmltags.DynamicContext;
import org.apache.ibatis.scripting.xmltags.SqlNode;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * like 模糊查询处理查询条件中包含 特殊字符
 * 参考：https://www.cnblogs.com/Gyoung/p/5876632.html
 * @author torres
 */
@Intercepts({@Signature(method = "query", type = Executor.class, args = {MappedStatement.class, Object.class,
        RowBounds.class, ResultHandler.class}),
        @Signature(method = "query", type = Executor.class, args = {MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})})
public class QueryExecutorInterceptor implements Interceptor {
   
    private static final ObjectFactory DEFAULT_OBJECT_FACTORY = new DefaultObjectFactory();
    private static final ObjectWrapperFactory DEFAULT_OBJECT_WRAPPER_FACTORY = new DefaultObjectWrapperFactory();
    private static final ReflectorFactory DEFAULT_OBJECT_REFLECTOR_FACTORY = new DefaultReflectorFactory();
    private static final String ROOT_SQL_NODE = "sqlSource.rootSqlNode";


    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        System.out.println("=======QueryExecutorInterceptor====invocation.getMethod()======"+invocation.getMethod());
        System.out.println("=======QueryExecutorInterceptor====invocation.getTarget()======"+invocation.getTarget());
        System.out.println("=======QueryExecutorInterceptor====invocation.getArgs()======"+invocation.getArgs());
       System.out.println("============QueryExecutorInterceptor==============start=============");
       System.out.println("============QueryExecutorInterceptor==========getArgs="+Arrays.asList(invocation.getArgs()));
        if(!(invocation.getArgs()[1] instanceof Map)) {
            if(invocation.getArgs()[1] instanceof String){
                //do nothing
            }else if(invocation.getArgs()[1] instanceof Integer){
                //do nothing
            }else {
                invocation.getArgs()[1] = transBean2Map(invocation.getArgs()[1]);
               System.out.println("============QueryExecutorInterceptor=======输入参数类型为自定义类型 ，转map后==="+invocation.getArgs()[1]);
            }
        }
        Object parameter = invocation.getArgs()[1];
        //对应一个mapper里的方法块
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        //抽象sql资源接口，实现类有动态sql等。。。
        SqlSource sqlSource =statement.getSqlSource();

       // System.out.println("===sql===="+sqlSource.getBoundSql(invocation.getArgs()[0]).getSql());

       System.out.println("============QueryExecutorInterceptor==========sqlSource="+sqlSource.getClass().getName());

        MetaObject metaMappedStatement = MetaObject.forObject(statement, DEFAULT_OBJECT_FACTORY, DEFAULT_OBJECT_WRAPPER_FACTORY, DEFAULT_OBJECT_REFLECTOR_FACTORY);

       System.out.println("============QueryExecutorInterceptor==========GetterNames="+Arrays.asList(metaMappedStatement.getGetterNames()));
       //封装好的sql mapper 对象
       BoundSql boundSql = statement.getBoundSql(parameter);
        System.out.println("=====boundSql==parameterObject=="+boundSql.getParameterObject());
        System.out.println("=====boundSql==parameterMapping=="+boundSql.getParameterMappings());
        System.out.println("=====boundSql==sql=="+boundSql.getSql());
     //   if (metaMappedStatement.hasGetter(ROOT_SQL_NODE)) {
            //修改参数值
            SqlNode sqlNode = (SqlNode) metaMappedStatement.getValue(ROOT_SQL_NODE);
            getBoundSql(statement.getConfiguration(), boundSql.getParameterObject(), sqlNode);
       // }
       System.out.println("============QueryExecutorInterceptor==============end=============");
        return invocation.proceed();
    }
    
	public static Map<String, Object> transBean2Map(Object obj) {
		if (obj == null) {
			return null;
		}
		Map<String, Object> map = new HashMap<>();
		try {
			BeanInfo beanInfo = Introspector.getBeanInfo(obj.getClass());
			PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
			for (PropertyDescriptor property : propertyDescriptors) {
				String key = property.getName();
				if (!key.equals("class")) {
					Method getter = property.getReadMethod();
					Object value = getter.invoke(obj);

					map.put(key, value);
				}
			}
		} catch (Exception e) {
		}
		return map;
	}

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }


    public static BoundSql getBoundSql(Configuration configuration, Object parameterObject, SqlNode sqlNode) {
        DynamicContext context = new DynamicContext(configuration, parameterObject);
        sqlNode.apply(context);
        String countextSql = context.getSql();
        System.out.println("=======countextSql========"+countextSql);
        SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
        Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
        String sql = modifyLikeSql(countextSql, parameterObject);
        System.out.println("==============="+sql);
        SqlSource sqlSource = sqlSourceParser.parse(sql, parameterType, context.getBindings());
        BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
        for (Map.Entry<String, Object> entry : context.getBindings().entrySet()) {
            boundSql.setAdditionalParameter(entry.getKey(), entry.getValue());
        }
        return boundSql;
    }

    public static String modifyLikeSql(String sql, Object parameterObject) {
        if (!(parameterObject instanceof Map)) {
            return sql;
        }
        if (!sql.toLowerCase().contains("like")) {
            return sql;
        }
        String reg = "\\bLIKE\\b.*\\#\\{\\b.*\\}";
        Pattern pattern = Pattern.compile(reg, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);

        List<String> replaceFiled = new ArrayList<String>();

        while (matcher.find()) {
            int n = matcher.groupCount();
            for (int i = 0; i <= n; i++) {
                String output = matcher.group(i);
                if (output != null) {
                    String key = getParameterKey(output);
                    if (replaceFiled.indexOf(key) < 0) {
                        replaceFiled.add(key);
                    }
                }
            }
        }
        //修改参数
        Map<String, Object> paramMab = (Map) parameterObject;
       System.out.println("==============QueryExecutorInterceptor============原始参数paramMab============="+paramMab);
        for (String key : replaceFiled) {
            Object val = paramMab.get(key);
            if (val != null && val instanceof String && (val.toString().contains("%") || val.toString().contains("_"))) {
                val = val.toString().replaceAll("/","").replaceAll("%", "/%").replaceAll("_", "/_");
                if (paramMab.containsKey(key)) {
                    paramMab.put(key, val);
                }
            }

        }
       System.out.println("============QueryExecutorInterceptor==============修改后参数paramMab============="+paramMab);
        return sql;
    }

    private static String getParameterKey(String input) {
        String key = "";
        String[] temp = input.split("#");
        if (temp.length > 1) {
            key = temp[1];
            key = key.replace("{", "").replace("}", "").split(",")[0];
        }
       System.out.println("==================QueryExecutorInterceptor=============="+key);
        return key.trim();
    }
}
