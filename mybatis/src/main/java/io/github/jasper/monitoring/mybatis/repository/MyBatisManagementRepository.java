package io.github.jasper.monitoring.mybatis.repository;

import io.github.jasper.monitoring.api.management.ManagementPage;
import io.github.jasper.monitoring.api.management.ManagementPageRequest;
import io.github.jasper.monitoring.api.management.model.AlertView;
import io.github.jasper.monitoring.api.management.model.ControlView;
import io.github.jasper.monitoring.api.management.model.RuleView;
import io.github.jasper.monitoring.api.management.model.SecurityEventView;
import io.github.jasper.monitoring.api.management.model.WhitelistView;
import io.github.jasper.monitoring.api.management.query.AlertQuery;
import io.github.jasper.monitoring.api.management.query.ControlQuery;
import io.github.jasper.monitoring.api.management.query.RuleQuery;
import io.github.jasper.monitoring.api.management.query.SecurityEventQuery;
import io.github.jasper.monitoring.api.management.query.WhitelistQuery;
import io.github.jasper.monitoring.core.domain.management.ManagementAuditRecord;
import io.github.jasper.monitoring.core.port.ManagementAuditRepository;
import io.github.jasper.monitoring.core.port.ManagementQueryRepository;
import io.github.jasper.monitoring.mybatis.mapper.ManagementAuditMapper;
import io.github.jasper.monitoring.mybatis.mapper.ManagementQueryMapper;
import io.github.jasper.monitoring.mybatis.po.ManagementRowPo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;

/** MyBatis-only adapter for scope-constrained management views and append-only audit. */
public final class MyBatisManagementRepository implements ManagementQueryRepository, ManagementAuditRepository {
    private final SqlSessionManager sessions;

    public MyBatisManagementRepository(SqlSessionManager sessions) { this.sessions = sessions; }

    @Override public ManagementPage<SecurityEventView> searchEvents(String scope, SecurityEventQuery query) {
        return read(session -> { ManagementQueryMapper mapper = session.getMapper(ManagementQueryMapper.class);
            ManagementPageRequest page = query.getPage();
            return page(events(mapper.events(scope, query.getFrom(), query.getTo(), page.getSort().name(),
                page.isDescending(), page.getSize(), offset(page)), scope), page, mapper.countEvents(scope, query.getFrom(), query.getTo())); });
    }
    @Override public Optional<SecurityEventView> findEventView(String scope, String id) {
        return read(session -> optionalEvent(session.getMapper(ManagementQueryMapper.class).event(scope, id), scope));
    }
    @Override public ManagementPage<AlertView> searchAlerts(String scope, AlertQuery query) {
        return read(session -> { ManagementQueryMapper mapper = session.getMapper(ManagementQueryMapper.class);
            ManagementPageRequest p=query.getPage(); return page(alerts(mapper.alerts(scope,p.getSize(),offset(p)),scope),p,mapper.countAlerts(scope)); });
    }
    @Override public Optional<AlertView> findAlertView(String scope, String id) {
        return read(session -> optionalAlert(session.getMapper(ManagementQueryMapper.class).alert(scope,id),scope));
    }
    @Override public boolean transitionAlert(String scope,String id,long version,String status) {
        return write(session -> session.getMapper(ManagementQueryMapper.class).transitionAlert(scope,id,version,status)==1);
    }
    @Override public ManagementPage<RuleView> searchRules(String scope, RuleQuery query) {
        return read(session -> { ManagementQueryMapper mapper=session.getMapper(ManagementQueryMapper.class); ManagementPageRequest p=query.getPage();
            return page(rules(mapper.rules(p.getSize(),offset(p)),scope),p,mapper.countRules()); });
    }
    @Override public Optional<RuleView> findRuleView(String scope,String id) {
        return read(session -> { ManagementRowPo row=session.getMapper(ManagementQueryMapper.class).rule(id);
            return row==null?Optional.<RuleView>empty():Optional.of(RuleView.of(row.getId(),scope)); });
    }
    @Override public ManagementPage<WhitelistView> searchWhitelists(String scope,WhitelistQuery query) {
        return read(session -> { ManagementQueryMapper mapper=session.getMapper(ManagementQueryMapper.class); ManagementPageRequest p=query.getPage();
            return page(whitelists(mapper.whitelists(scope,p.getSize(),offset(p)),scope),p,mapper.countWhitelists(scope)); });
    }
    @Override public Optional<WhitelistView> findWhitelistView(String scope,String id) {
        return read(session -> { ManagementRowPo row=session.getMapper(ManagementQueryMapper.class).whitelist(scope,id);
            return row==null?Optional.<WhitelistView>empty():Optional.of(WhitelistView.of(row.getId(),scope)); });
    }
    @Override public boolean transitionWhitelist(String scope,String id,long version,boolean active,String actorId,String reason) {
        return write(session -> session.getMapper(ManagementQueryMapper.class).transitionWhitelist(scope,id,version,
            active?"ACTIVE":"REVOKED",actorId,reason)==1);
    }
    @Override public ManagementPage<ControlView> searchControls(String scope,ControlQuery query) {
        return read(session -> { ManagementQueryMapper mapper=session.getMapper(ManagementQueryMapper.class); ManagementPageRequest p=query.getPage();
            return page(controls(mapper.controls(scope,query.getFrom(),query.getTo(),p.getSize(),offset(p)),scope),p,
                mapper.countControls(scope,query.getFrom(),query.getTo())); });
    }
    @Override public Optional<ControlView> findControlView(String scope,String id) {
        return read(session -> optionalControl(session.getMapper(ManagementQueryMapper.class).control(scope,id),scope));
    }
    @Override public boolean transitionControl(String scope,String id,long version,String expected,String target,String reason) {
        return write(session -> session.getMapper(ManagementQueryMapper.class).transitionControl(scope,id,version,expected,target,reason)==1);
    }
    @Override public void append(ManagementAuditRecord record) {
        write(session -> session.getMapper(ManagementAuditMapper.class).insert(record));
    }

    private <T> T read(Function<SqlSession,T> work) { boolean owner=!sessions.isManagedSessionStarted(); if(owner)sessions.startManagedSession(true);
        try{return work.apply(sessions);}finally{if(owner)sessions.close();} }
    private <T> T write(Function<SqlSession,T> work) { boolean owner=!sessions.isManagedSessionStarted(); if(owner)sessions.startManagedSession(false);
        try{T value=work.apply(sessions);if(owner)sessions.commit();return value;}catch(RuntimeException e){if(owner)sessions.rollback();throw e;}finally{if(owner)sessions.close();} }
    private static long offset(ManagementPageRequest p){return ((long)p.getPage())*p.getSize();}
    private static <T> ManagementPage<T> page(List<T> rows,ManagementPageRequest p,long total){return ManagementPage.of(rows,p.getPage(),p.getSize(),total);}
    private static List<SecurityEventView> events(List<ManagementRowPo> rows,String scope){List<SecurityEventView> out=new ArrayList<SecurityEventView>();for(ManagementRowPo r:rows)out.add(SecurityEventView.of(r.getId(),scope));return out;}
    private static List<AlertView> alerts(List<ManagementRowPo> rows,String scope){List<AlertView> out=new ArrayList<AlertView>();for(ManagementRowPo r:rows)out.add(AlertView.of(r.getId(),scope,r.getVersion()));return out;}
    private static List<RuleView> rules(List<ManagementRowPo> rows,String scope){List<RuleView> out=new ArrayList<RuleView>();for(ManagementRowPo r:rows)out.add(RuleView.of(r.getId(),scope));return out;}
    private static List<WhitelistView> whitelists(List<ManagementRowPo> rows,String scope){List<WhitelistView> out=new ArrayList<WhitelistView>();for(ManagementRowPo r:rows)out.add(WhitelistView.of(r.getId(),scope));return out;}
    private static List<ControlView> controls(List<ManagementRowPo> rows,String scope){List<ControlView> out=new ArrayList<ControlView>();for(ManagementRowPo r:rows)out.add(ControlView.of(r.getId(),scope,r.getStatus(),r.getVersion()));return out;}
    private static Optional<SecurityEventView> optionalEvent(ManagementRowPo r,String scope){return r==null?Optional.<SecurityEventView>empty():Optional.of(SecurityEventView.of(r.getId(),scope));}
    private static Optional<AlertView> optionalAlert(ManagementRowPo r,String scope){return r==null?Optional.<AlertView>empty():Optional.of(AlertView.of(r.getId(),scope,r.getVersion()));}
    private static Optional<ControlView> optionalControl(ManagementRowPo r,String scope){return r==null?Optional.<ControlView>empty():Optional.of(ControlView.of(r.getId(),scope,r.getStatus(),r.getVersion()));}
}
