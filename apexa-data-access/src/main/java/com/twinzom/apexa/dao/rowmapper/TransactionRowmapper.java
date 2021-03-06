package com.twinzom.apexa.dao.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

import org.springframework.jdbc.core.RowMapper;

import com.twinzom.apexa.dao.po.TransactionPo;


public class TransactionRowmapper implements RowMapper<TransactionPo> {

	public TransactionPo mapRow(ResultSet rs, int rowNum) throws SQLException {
		TransactionPo transaction = new TransactionPo();
        
		transaction.setTxnid(rs.getLong("txn_id"));
		transaction.setTxnTypeCde(rs.getString("txn_type_cde"));
		transaction.setExtTxnRef(rs.getString("ext_txn_ref"));
		transaction.setExtTxnTypeCde(rs.getString("ext_txn_type_cde"));
		transaction.setAcid(rs.getLong("acid"));
		transaction.setSecid(rs.getLong("secid"));
		transaction.setExeDtTm(rs.getDate("exe_dt_tm"));
		transaction.setPostDtTm(rs.getDate("post_dt_tm"));
		transaction.setExePrc(rs.getFloat("exe_prc"));
		transaction.setPrcCcy(rs.getString("prc_ccy"));
		transaction.setQty(rs.getFloat("qty"));
		transaction.setPripAmtLocl(rs.getFloat("prip_amt_locl"));
		transaction.setSetlDtTm(rs.getDate("setl_dt_tm"));
		transaction.setSetlCcy(rs.getString("setl_ccy"));
		transaction.setSetlAmtSetl(rs.getFloat("setl_amt_setl"));
		transaction.setSetlLoclRate(rs.getFloat("setl_locl_rate"));
		transaction.setMktCde(rs.getString("mkt_cde"));
		transaction.setSrcSysCde(rs.getString("src_sys_cde"));
		transaction.setCfmInd(rs.getString("cfm_ind"));
		transaction.setLongShtInd(rs.getString("long_sht_ind"));
		transaction.setMktValLocl(rs.getFloat("mkt_val_locl"));
		transaction.setMktValAcct(rs.getFloat("mkt_val_acct"));
		transaction.setBkCostLocl(rs.getFloat("bk_cost_locl"));
		transaction.setBkCostAcct(rs.getFloat("bk_cost_acct"));
		transaction.setExtLotRef(rs.getString("ext_lot_ref"));
		transaction.setTrustTypeCde(rs.getString("trust_type_cde"));
		
        return transaction;
        
    }
	
}
