package com.twinzom.apexa.apis.service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twinzom.apexa.apis.model.AccountHoldings;
import com.twinzom.apexa.apis.model.Holding;
import com.twinzom.apexa.apis.model.Security;
import com.twinzom.apexa.apis.remoteclient.CalEngClient;
import com.twinzom.apexa.caleng.model.CalGroup;
import com.twinzom.apexa.caleng.model.CalPosition;
import com.twinzom.apexa.caleng.model.CalResponse;
import com.twinzom.apexa.caleng.model.CalSnapshot;
import com.twinzom.apexa.caleng.model.CalTxn;
import com.twinzom.apexa.dao.AccountDao;
import com.twinzom.apexa.dao.SecurityDao;
import com.twinzom.apexa.dao.TransactionDao;
import com.twinzom.apexa.dao.po.AccountPo;
import com.twinzom.apexa.dao.po.SecurityPo;
import com.twinzom.apexa.dao.po.TransactionPo;

@Service
public class GetHoldingsByAcctNumsService {

	@Autowired
	AccountDao accountDao;
	
	@Autowired
	TransactionDao transactionDao;
	
	@Autowired
	SecurityDao securityDao;
	
	@Autowired
	CalEngClient calEngClient;
	
	private static  SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
	
	public AccountHoldings process (String acctNum, String datesStr) throws InterruptedException, ExecutionException {
		
		/**
		 * convert external account id to internal account id
		 */
		//TODO: valid input account number list
		
		
		AccountPo apo = accountDao.getAcctByAccNum(acctNum);
		
		// Format acid list
		List<Long> acids = new ArrayList<Long>();
		Map<Long, String> acidAcctNumMap = new HashMap<Long, String>();
		acids.add(apo.getAcid());
		acidAcctNumMap.put(apo.getAcid(), apo.getAcctNum());
		
		// Get transactions from DB
		List<TransactionPo> tpos = transactionDao.getTxnsByAcctIds(acids);
		
		Map<Long, Map<Long, List<TransactionPo>>> groupByAcidSecidMap = 
				tpos.stream().collect(Collectors.groupingBy(TransactionPo::getAcid, Collectors.groupingBy(TransactionPo::getSecid)));
		
		List<CompletableFuture<CalResponse>> pages = new ArrayList<CompletableFuture<CalResponse>>();
		
		for (Long acid : groupByAcidSecidMap.keySet()) {
			Map<Long, List<TransactionPo>> groupBySecidMap = groupByAcidSecidMap.get(acid);
			List<CalGroup> calGroups = formatCalculateHoldingSnapshotsBody(groupBySecidMap);
			pages.add(calEngClient.callCalculateHoldingSnapshots(calGroups, datesStr));
		}
		
		CompletableFuture.allOf(pages.toArray(new CompletableFuture[pages.size()])).join();
		
		CalResponse calResponse = pages.get(0).get();
		
		Map<String, List<Holding>> holdingGroup = groupHoldingsByDate(calResponse);
		
		populateSecurityMkVal(holdingGroup);
		
		AccountHoldings acctHldgs = new AccountHoldings();
		
		acctHldgs.setAcctNum(acctNum);
		
		acctHldgs.setHoldingGroup(holdingGroup);
		
//		ObjectMapper om = new ObjectMapper();
//		try {
//			System.out.println(om.writerWithDefaultPrettyPrinter().writeValueAsString(acctHldgs));
//		} catch (JsonProcessingException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		return acctHldgs;
	}
	
	private void populateSecurityMkVal (Map<String, List<Holding>> holdingGroup) {
		
		List<Long> secids = new ArrayList<Long>();
		
		
		
		for (List<Holding> holdings : holdingGroup.values()) {
			List<Holding> zeroHoldings = new ArrayList<Holding>();
			for (Holding h : holdings) {
				if (h.getQty().compareTo(BigDecimal.ZERO) == 0)
					zeroHoldings.add(h);
				else 
					secids.add(h.getSecid());
			}
			holdings.removeAll(zeroHoldings);
		}
		
		List<SecurityPo> secPos = securityDao.getSecsBySecids(secids);
		
		Map<Long, Security> secs = convertSecPoToSec (secPos);
		
		Map<String, Double> rates = getRates();
		
		for (List<Holding> holdings : holdingGroup.values()) {
			for (Holding h : holdings) {
				Security s = secs.get(h.getSecid());
				h.setSecurity(s);
				System.out.println(h.getQty().multiply(BigDecimal.valueOf(s.getPrice())));
				h.setMktValLocl(h.getQty().multiply(BigDecimal.valueOf(s.getPrice())));
				h.setMktValAcct(h.getQty().multiply(BigDecimal.valueOf(s.getPrice())).multiply(BigDecimal.valueOf(rates.get(s.getCcy()))));
			}
		}
		
	}
	
	private Map<Long, Security> convertSecPoToSec (List<SecurityPo> secPos) {
		
		Map<Long, Security> secs = new HashMap<Long, Security>();
		
		for (SecurityPo spo : secPos) {
			Security sec = new Security();
			sec.setSecid(spo.getSecid());
			sec.setCode(spo.getSecCde());
			sec.setName(spo.getName());
			sec.setCcy(spo.getCcy());
			sec.setRiskLvl(spo.getRiskLvl());
			sec.setPrice(spo.getNavPrc());
			sec.setStdev(spo.getStdDv());
			sec.setAnnRtrn(spo.getAnnRtrn());
			sec.setSharpe(spo.getSharpe());
			secs.put(sec.getSecid(), sec);
		}
		
		return secs;
		
	}
	
	private Map<String, List<Holding>> groupHoldingsByDate (CalResponse calResponse) {
		
		Map<String, List<Holding>> groupByDate = new HashMap<String, List<Holding>>();
		
		for (String secidStr : calResponse.getSnapshotsByGroup().keySet()) {
			
			List<CalSnapshot> snapshots = calResponse.getSnapshotsByGroup().get(secidStr);
			
			for (CalSnapshot snapshot : snapshots) {
				
				CalPosition p = snapshot.getPosition();
				
				List<Holding> holdings = groupByDate.get(snapshot.getSnapshotKey());
				if (holdings == null) {
					holdings = new ArrayList<Holding>();
					groupByDate.put(snapshot.getSnapshotKey(), holdings);
				}
				Holding h = new Holding();
				h.setSecid(Long.parseLong(secidStr));
				h.setQty(p.getQty());
				h.setBkCostLocl(p.getBkCostLocl());
				h.setBkCostAcct(p.getBkCostAcct());
				holdings.add(h);
			}
		}
		
		return groupByDate;
		
	}
	
	private List<CalGroup> formatCalculateHoldingSnapshotsBody (Map<Long, List<TransactionPo>> groupBySecidMap) {
		
		List<CalGroup> groups = new ArrayList<CalGroup>();
		
		for (Long secid: groupBySecidMap.keySet()) {
			CalGroup g = new CalGroup();
			g.setGroupId(String.valueOf(secid));
			
			List<CalTxn> calTxns = new ArrayList<CalTxn>();
			g.setTxns(calTxns);
			
			List<TransactionPo> txns = groupBySecidMap.get(secid);
			
			for (TransactionPo t : txns) {
				CalTxn calTxn = new CalTxn();
				calTxn.setId(String.valueOf(t.getTxnid()));
				calTxn.setTxnTypeCde(t.getTxnTypeCde());
				calTxn.setTrdDtTm(t.getExeDtTm());
				calTxn.setPrc(BigDecimal.valueOf(t.getExePrc()));
				calTxn.setPrcCcy(t.getPrcCcy());
				calTxn.setQty(BigDecimal.valueOf(t.getQty()));
				calTxn.setPripLocl(BigDecimal.valueOf(t.getPripAmtLocl()));
				calTxn.setSetlDtTm(t.getSetlDtTm());
				calTxn.setSetlAmtSetl(BigDecimal.valueOf(t.getSetlAmtSetl()));
				calTxn.setSetlCcy(t.getSetlCcy());
				calTxn.setMktValLocl(BigDecimal.valueOf(t.getMktValLocl()));
				calTxn.setMktValAcct(BigDecimal.valueOf(t.getMktValAcct()));
				calTxn.setBkCostLocl(BigDecimal.valueOf(t.getBkCostLocl()));
				calTxn.setBkCostAcct(BigDecimal.valueOf(t.getBkCostAcct()));
				calTxns.add(calTxn);
			}
			groups.add(g);
		}
	
		return groups;
	}
	
	private Map<String, Double> getRates () {
		Map<String, Double> rateSeed = new HashMap<String, Double>();
		
		rateSeed.put("USD", 7.84990973);
		rateSeed.put("EUR", 8.82691 );
		rateSeed.put("AUD", 5.58616 );
		rateSeed.put("SGD", 5.80404 );
		rateSeed.put("GBP", 10.3373 );
		rateSeed.put("RMB", 1.17005 );
		rateSeed.put("NZD", 5.33381 );
		rateSeed.put("CAD", 5.90015 );
		rateSeed.put("CNY", 1.17005 );
		rateSeed.put("JPY", 0.0703964 );
		rateSeed.put("HKD", 1.0 );
		
		return rateSeed;
	}
	
}
