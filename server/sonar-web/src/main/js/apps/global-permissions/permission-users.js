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
import React from 'react';
import PermissionsUsersGroupsMixin from './permission-users-groups-mixin';
import UsersView from './users-view';

export default React.createClass({
  mixins: [PermissionsUsersGroupsMixin],

  renderUpdateLink() {
    return (
        <a onClick={this.updateUsers}
           className="icon-bullet-list"
           title="Update Users"
           data-toggle="tooltip"
           href="#"></a>
    );
  },

  renderItem(item) {
    return item.name;
  },

  renderTitle() {
    return 'Users';
  },

  updateUsers(e) {
    e.preventDefault();
    new UsersView({
      permission: this.props.permission.key,
      project: this.props.project,
      refresh: this.props.refresh
    }).render();
  }
});
