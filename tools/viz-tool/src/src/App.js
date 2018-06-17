import React from 'react';
import PropTypes from 'prop-types';
import {withStyles} from '@material-ui/core/styles';
import CssBaseline from '@material-ui/core/CssBaseline';
import TabBar from './TabBar';

const styles = theme => ({
    root: {
        flexGrow: 1,
        backgroundColor: theme.palette.background.paper,
    }
});

const App = ({ classes }) => {
    return (
        <div className={classes.root}>
            <CssBaseline/>
            <TabBar/>
        </div>
    );
};


App.propTypes = {
    classes: PropTypes.object.isRequired,
};

export default withStyles(styles)(App);
