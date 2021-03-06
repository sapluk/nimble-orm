package com.pugwoo.dbhelper.utils;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;

import com.pugwoo.dbhelper.annotation.Column;
import com.pugwoo.dbhelper.annotation.JoinLeftTable;
import com.pugwoo.dbhelper.annotation.JoinRightTable;
import com.pugwoo.dbhelper.annotation.JoinTable;
import com.pugwoo.dbhelper.exception.RowMapperFailException;

/**
 * 2015年1月13日 17:48:30<br>
 * 抽取出来的根据注解来生成bean的rowMapper
 * 
 * @param <T>
 */
public class AnnotationSupportRowMapper<T> implements RowMapper<T> {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationSupportRowMapper.class);

	private Class<T> clazz;
	private boolean isUseGivenObj = false;
	private T t;
	
	private boolean isJoinVO = false;
	private Field leftJoinField;
	private Field rightJoinField;

	private boolean selectOnlyKey = false; // 是否只选择主键列，默认false
	
	public AnnotationSupportRowMapper(Class<T> clazz) {
		handleClazz(clazz);
	}
	
	public AnnotationSupportRowMapper(Class<T> clazz, boolean selectOnlyKey) {
		this.selectOnlyKey = selectOnlyKey;
		handleClazz(clazz);
	}
	
	public AnnotationSupportRowMapper(Class<T> clazz, T t) {
		handleClazz(clazz);
		this.t = t;
		this.isUseGivenObj = true;
	}
	
	private void handleClazz(Class<T> clazz) {
		this.clazz = clazz;
		JoinTable joinTable = DOInfoReader.getJoinTable(clazz);
		if(joinTable != null) {
			isJoinVO = true;
			leftJoinField = DOInfoReader.getJoinLeftTable(clazz);
			rightJoinField = DOInfoReader.getJoinRightTable(clazz);
		}
	}

	@Override
	public T mapRow(ResultSet rs, int index) throws SQLException {
		try {
			T obj = isUseGivenObj ? t : clazz.newInstance();
			
			if(isJoinVO) {
				Object t1 = leftJoinField.getType().newInstance();
				Object t2 = rightJoinField.getType().newInstance();
				
				JoinLeftTable joinLeftTable = leftJoinField.getAnnotation(JoinLeftTable.class);
				JoinRightTable joinRightTable = rightJoinField.getAnnotation(JoinRightTable.class);
				
				// 如果关联对象的所有字段都是null值，那么该对象设置为null值
				
				boolean isT1AllNull = true;
				List<Field> fieldsT1 = DOInfoReader.getColumnsForSelect(leftJoinField.getType(), selectOnlyKey);
				for (Field field : fieldsT1) {
					Column column = field.getAnnotation(Column.class);
					String columnName;
					if(!column.computed().trim().isEmpty()) {
						columnName = column.value(); // 计算列用用户自行制定别名
					} else {
						columnName = joinLeftTable.alias() + "." + column.value();
					}
					Object value = TypeAutoCast.cast(
						TypeAutoCast.getFromRS(rs, columnName, field), 
						field.getType());
					if(value != null) {
						isT1AllNull = false;
					}
					DOInfoReader.setValue(field, t1, value);
				}
				
				boolean isT2AllNull = true;
				List<Field> fieldsT2 = DOInfoReader.getColumnsForSelect(rightJoinField.getType(), selectOnlyKey);
				for (Field field : fieldsT2) {
					Column column = field.getAnnotation(Column.class);
					String columnName;
					if(!column.computed().trim().isEmpty()) {
						columnName = column.value(); // 计算列用用户自行制定别名
					} else {
						columnName = joinRightTable.alias() + "." + column.value();
					}
					Object value = TypeAutoCast.cast(
						TypeAutoCast.getFromRS(rs, columnName, field), 
						field.getType());
					if(value != null) {
						isT2AllNull = false;
					}
					DOInfoReader.setValue(field, t2, value);
				}
				
				DOInfoReader.setValue(leftJoinField, obj, isT1AllNull ? null : t1);
				DOInfoReader.setValue(rightJoinField, obj, isT2AllNull ? null : t2);
				
			} else {
				List<Field> fields = DOInfoReader.getColumnsForSelect(clazz, selectOnlyKey);
				for (Field field : fields) {
					Column column = field.getAnnotation(Column.class);
					Object value = TypeAutoCast.cast(
							TypeAutoCast.getFromRS(rs, column.value(), field), 
							field.getType());
					DOInfoReader.setValue(field, obj, value);
				}
			}
			
			return obj;
		} catch (Exception e) {
			LOGGER.error("mapRow exception", e);
			throw new RowMapperFailException(e);
		}
	}
}