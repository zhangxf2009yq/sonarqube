/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.db.purge;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.annotation.Nonnull;
import org.apache.commons.lang.ArrayUtils;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.SqlSession;
import org.sonar.api.resources.Scopes;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.db.Dao;
import org.sonar.db.DbSession;
import org.sonar.db.MyBatis;
import org.sonar.db.component.ResourceDao;
import org.sonar.db.component.ResourceDto;

import static java.util.Collections.emptyList;
import static org.sonar.api.utils.DateUtils.dateToLong;
import static org.sonar.db.DatabaseUtils.executeLargeInputs;

/**
 * @since 2.14
 */
public class PurgeDao implements Dao {
  private static final Logger LOG = Loggers.get(PurgeDao.class);
  private static final String[] UNPROCESSED_STATUS = new String[] {"U"};

  private final MyBatis mybatis;
  private final ResourceDao resourceDao;
  private final System2 system2;

  public PurgeDao(MyBatis mybatis, ResourceDao resourceDao, System2 system2) {
    this.mybatis = mybatis;
    this.resourceDao = resourceDao;
    this.system2 = system2;
  }

  public PurgeDao purge(PurgeConfiguration conf, PurgeListener listener, PurgeProfiler profiler) {
    DbSession session = mybatis.openSession(true);
    try {
      purge(session, conf, listener, profiler);
      session.commit();
    } finally {
      MyBatis.closeQuietly(session);
    }
    return this;
  }

  public void purge(DbSession session, PurgeConfiguration conf, PurgeListener listener, PurgeProfiler profiler) {
    PurgeMapper mapper = session.getMapper(PurgeMapper.class);
    PurgeCommands commands = new PurgeCommands(session, mapper, profiler);
    List<ResourceDto> projects = getProjects(conf.rootProjectIdUuid().getId(), session);
    for (ResourceDto project : projects) {
      LOG.debug("-> Clean " + project.getLongName() + " [id=" + project.getId() + "]");
      deleteAbortedBuilds(project, commands);
      purge(project, conf.scopesWithoutHistoricalData(), commands);
    }
    for (ResourceDto project : projects) {
      disableOrphanResources(project, session, mapper, listener);
    }
    deleteOldClosedIssues(conf, mapper, listener);
  }

  private static void deleteOldClosedIssues(PurgeConfiguration conf, PurgeMapper mapper, PurgeListener listener) {
    Date toDate = conf.maxLiveDateOfClosedIssues();
    List<String> issueKeys = mapper.selectOldClosedIssueKeys(conf.rootProjectIdUuid().getUuid(), dateToLong(toDate));
    executeLargeInputs(issueKeys, new DeleteIssueChangesFromIssueKeys(mapper));
    executeLargeInputs(issueKeys, new DeleteIssuesFromKeys(mapper));
    listener.onIssuesRemoval(issueKeys);
  }

  private static void deleteAbortedBuilds(ResourceDto project, PurgeCommands commands) {
    LOG.debug("<- Delete aborted builds");
    PurgeSnapshotQuery query = PurgeSnapshotQuery.create()
      .setIslast(false)
      .setStatus(UNPROCESSED_STATUS)
      .setRootProjectId(project.getId());
    commands.deleteSnapshots(query);
  }

  private static void purge(ResourceDto project, String[] scopesWithoutHistoricalData, PurgeCommands purgeCommands) {
    List<Long> projectSnapshotIds = purgeCommands.selectSnapshotIds(
      PurgeSnapshotQuery.create()
        .setResourceId(project.getId())
        .setIslast(false)
        .setNotPurged(true));
    for (Long projectSnapshotId : projectSnapshotIds) {
      LOG.debug("<- Clean snapshot " + projectSnapshotId);
      if (!ArrayUtils.isEmpty(scopesWithoutHistoricalData)) {
        PurgeSnapshotQuery query = PurgeSnapshotQuery.create()
          .setIslast(false)
          .setScopes(scopesWithoutHistoricalData)
          .setRootSnapshotId(projectSnapshotId);
        purgeCommands.deleteSnapshots(query);
      }

      // must be executed at the end for reentrance
      purgeCommands.purgeSnapshots(
        PurgeSnapshotQuery.create().setRootSnapshotId(projectSnapshotId).setNotPurged(true),
        PurgeSnapshotQuery.create().setId(projectSnapshotId).setNotPurged(true));
    }
  }

  private void disableOrphanResources(final ResourceDto project, final SqlSession session, final PurgeMapper purgeMapper, final PurgeListener purgeListener) {
    final List<IdUuidPair> componentIdUuids = new ArrayList<>();
    session.select("org.sonar.db.purge.PurgeMapper.selectComponentIdUuidsToDisable", project.getId(), new ResultHandler() {
      @Override
      public void handleResult(ResultContext resultContext) {
        IdUuidPair componentIdUuid = (IdUuidPair) resultContext.getResultObject();
        if (componentIdUuid.getId() != null) {
          componentIdUuids.add(componentIdUuid);
        }
      }
    });

    for (IdUuidPair componentIdUuid : componentIdUuids) {
      disableResource(componentIdUuid, purgeMapper);
      purgeListener.onComponentDisabling(componentIdUuid.getUuid());
    }

    session.commit();
  }

  public List<PurgeableSnapshotDto> selectPurgeableSnapshots(long resourceId) {
    DbSession session = mybatis.openSession(true);
    try {
      return selectPurgeableSnapshots(resourceId, session);
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public List<PurgeableSnapshotDto> selectPurgeableSnapshots(long resourceId, DbSession session) {
    List<PurgeableSnapshotDto> result = Lists.newArrayList();
    result.addAll(mapper(session).selectPurgeableSnapshotsWithEvents(resourceId));
    result.addAll(mapper(session).selectPurgeableSnapshotsWithoutEvents(resourceId));
    // sort by date
    Collections.sort(result);
    return result;
  }

  public PurgeDao deleteProject(DbSession session, String uuid) {
    PurgeProfiler profiler = new PurgeProfiler();
    PurgeCommands purgeCommands = new PurgeCommands(session, profiler);
    deleteProject(uuid, mapper(session), purgeCommands);
    return this;
  }

  private static void deleteProject(String rootUuid, PurgeMapper mapper, PurgeCommands commands) {
    List<IdUuidPair> childrenIds = mapper.selectComponentsByProjectUuid(rootUuid);
    commands.deleteComponents(childrenIds);
    commands.deleteFileSources(rootUuid);
    commands.deleteCeActivity(rootUuid);
  }

  private void disableResource(IdUuidPair componentIdUuid, PurgeMapper mapper) {
    long componentId = componentIdUuid.getId();
    mapper.deleteResourceIndex(Arrays.asList(componentId));
    mapper.setSnapshotIsLastToFalse(componentId);
    mapper.deleteFileSourcesByUuid(componentIdUuid.getUuid());
    mapper.disableResource(componentId);
    mapper.resolveResourceIssuesNotAlreadyResolved(componentIdUuid.getUuid(), system2.now());
  }

  public PurgeDao deleteSnapshots(PurgeSnapshotQuery query, PurgeProfiler profiler) {
    final DbSession session = mybatis.openSession(true);
    try {
      return deleteSnapshots(session, profiler, query);

    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public PurgeDao deleteSnapshots(DbSession session, PurgeProfiler profiler, PurgeSnapshotQuery... queries) {
    new PurgeCommands(session, profiler).deleteSnapshots(queries);
    return this;
  }

  /**
   * Load the whole tree of projects, including the project given in parameter.
   */
  private List<ResourceDto> getProjects(long rootId, SqlSession session) {
    return resourceDao.selectWholeTreeForRootId(session, rootId, Scopes.PROJECT);
  }

  private static PurgeMapper mapper(DbSession session) {
    return session.getMapper(PurgeMapper.class);
  }

  private static class DeleteIssueChangesFromIssueKeys implements Function<List<String>, List<Void>> {
    private final PurgeMapper mapper;

    private DeleteIssueChangesFromIssueKeys(PurgeMapper mapper) {
      this.mapper = mapper;
    }

    @Override
    public List<Void> apply(@Nonnull List<String> input) {
      mapper.deleteIssueChangesFromIssueKeys(input);
      return emptyList();
    }
  }

  private static class DeleteIssuesFromKeys implements Function<List<String>, List<Void>> {
    private final PurgeMapper mapper;

    private DeleteIssuesFromKeys(PurgeMapper mapper) {
      this.mapper = mapper;
    }

    @Override
    public List<Void> apply(@Nonnull List<String> input) {
      mapper.deleteIssuesFromKeys(input);
      return emptyList();
    }
  }
}
