import React from 'react';
import PropTypes from 'prop-types';
import {matchPath} from 'react-router';
import AppBar from '@material-ui/core/AppBar';
import Tabs from '@material-ui/core/Tabs';
import Tab from '@material-ui/core/Tab';

class TabBar extends React.Component {
    tabs = [
        {
            label: "Summary",
            route: "/"
        },
        {
            label: "Task Graph",
            route: "/tasks"
        },
        {
            label: "Container Graph",
            route: "/containers"
        },
        {
            label: "Timeline",
            route: "/timeline"
        }
    ];

    handleChange = (event, value) => {
        const history = this.context.router.history;
        history.push(this.tabs[value].route);
    };

    render = () => {
        const selectedTab = this.getIndexOfSelectedTab();

        return <AppBar position="static" color="default">
            <Tabs
                value={selectedTab}
                onChange={this.handleChange}
                indicatorColor="primary"
                textColor="primary"
                fullWidth
            >
                {this.tabs.map(this.renderTab)}
            </Tabs>
        </AppBar>;
    };

    getIndexOfSelectedTab = () => this.tabs.findIndex(this.isSelectedTab);

    isSelectedTab = ({route}) => {
        const router = this.context.router;
        const location = router.history.location;

        return matchPath(location.pathname, {path: route, exact: true}, router) !== null;
    };

    renderTab = ({label, route}, index) => {
        return <Tab label={label} key={index}/>
    };

    static contextTypes = {
        router: PropTypes.shape({
            history: PropTypes.shape({
                push: PropTypes.func.isRequired,
            }).isRequired
        }).isRequired
    };
}

export default TabBar;
